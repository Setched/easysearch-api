"""Persistent browser session for calling Wildberries' internal search API.

Wildberries' antibot (WBAAS) fingerprints the browser below what JS-property patching can reach —
plain headless Playwright, even with hand-rolled stealth JS patches, was reliably detected (full
history of what didn't work in wildberries-scraper/README.md's Status section). What passes it:
[`patchright`](https://github.com/Kaliiiiiiiiii-Vinyzu/patchright-python) instead of plain
`playwright` (patches Chromium's DevTools-protocol-level automation tells, e.g. the
`Runtime.enable` CDP leak), driving a real headful Chrome (`channel="chrome"`, `headless=False`,
via Xvfb — see Dockerfile) with a persistent on-disk profile, and deliberately *no* manual
User-Agent or fingerprint faking on top — patchright's own docs warn that faking properties over
its patches makes the fingerprint more inconsistent, not less.

The actual request is made by navigating to the real search-results page and intercepting *its
own* call to the internal search API (`fetch_search()`'s `expect_response()`), rather than
crafting our own `fetch()` — the page's own JS is what needs to satisfy WBAAS.
"""

import logging
import random
import time
from urllib.parse import quote

from patchright.sync_api import Error as PlaywrightError
from patchright.sync_api import Page
from patchright.sync_api import TimeoutError as PlaywrightTimeoutError
from patchright.sync_api import sync_playwright

SEARCH_PAGE_URL = "https://www.wildberries.ru/catalog/0/search.aspx?search={query}"
SEARCH_URL_PREFIX = "https://www.wildberries.ru/__internal/u-search/"
PROFILE_DIR = "/app/.chrome-profile"

NAV_TIMEOUT_MS = 30_000
RESPONSE_TIMEOUT_MS = 90_000
MAX_RETRIES = 2


class WildberriesBlockedError(RuntimeError):
    """Raised when Wildberries' search API could not be reached or rejected the request."""


def _simulate_human_activity(page: Page) -> None:
    """Fakes a few seconds of plausible mouse/scroll activity for WBAAS's behavior tracker."""
    try:
        x, y = random.randint(150, 400), random.randint(150, 300)
        for _ in range(6):
            x = max(0, min(1900, x + random.randint(-80, 120)))
            y = max(0, min(1000, y + random.randint(-60, 90)))
            page.mouse.move(x, y, steps=random.randint(5, 15))
            page.wait_for_timeout(random.randint(150, 450))
        page.mouse.wheel(0, random.randint(200, 500))
        page.wait_for_timeout(random.randint(400, 900))
        page.mouse.wheel(0, random.randint(100, 300))
        page.wait_for_timeout(random.randint(1_500, 2_500))
    except PlaywrightError:
        pass


class WildberriesSession:
    """A single, reusable browser session. Not thread-safe, and its patchright objects are bound
    to whichever thread first launches them — callers must pin every call to one dedicated thread
    (see the single-worker executor in main.py), not just serialize with a lock."""

    def __init__(self) -> None:
        self._playwright = None
        self._context = None
        self._page = None

    def _launch(self) -> None:
        self._playwright = sync_playwright().start()
        # launch_persistent_context, real Chrome, headful, no viewport override, no UA/header
        # overrides — see module docstring for why each of these matters for patchright.
        self._context = self._playwright.chromium.launch_persistent_context(
            user_data_dir=PROFILE_DIR,
            channel="chrome",
            headless=False,
            no_viewport=True,
            locale="ru-RU",
        )

    def fetch_search(self, query: str, retries: int = MAX_RETRIES) -> dict:
        """Fetches the first page of search results as JSON, by intercepting the search-results
        page's own call to the internal search API as it renders — see module docstring."""
        for attempt in range(retries + 1):
            try:
                if self._context is None:
                    self._launch()
                self._page = self._context.new_page()
                url = SEARCH_PAGE_URL.format(query=quote(query))

                seen_urls = []
                self._page.on("request", lambda r: seen_urls.append(r.url))
                try:
                    with self._page.expect_response(
                            lambda r: r.url.startswith(SEARCH_URL_PREFIX),
                            timeout=RESPONSE_TIMEOUT_MS) as response_info:
                        self._page.goto(url, wait_until="load", timeout=NAV_TIMEOUT_MS)
                        _simulate_human_activity(self._page)
                except PlaywrightTimeoutError:
                    logging.getLogger("wildberries-scraper").warning(
                        "No matching request seen; page title=%r; %d requests, sample: %s",
                        self._page.title(), len(seen_urls), seen_urls[:40],
                    )
                    raise
                response = response_info.value

                if response.status == 200:
                    return response.json()
                logging.getLogger("wildberries-scraper").warning(
                    "Wildberries search API returned HTTP %s: %.500r",
                    response.status, response.text(),
                )
                raise WildberriesBlockedError(
                    f"Wildberries returned HTTP {response.status} for query {query!r}")
            except (WildberriesBlockedError, PlaywrightError, PlaywrightTimeoutError):
                if attempt < retries:
                    self.shutdown()
                    time.sleep(2.0)
                    continue
                raise
        raise WildberriesBlockedError("fetch_search: retries exhausted")

    def shutdown(self) -> None:
        self._page = None
        try:
            if self._context:
                self._context.close()
        except Exception:
            pass
        try:
            if self._playwright:
                self._playwright.stop()
        except Exception:
            pass
        self._context = None
        self._playwright = None
