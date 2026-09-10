"""Persistent browser session for calling Wildberries' internal search API.

v2 approach (see wildberries-scraper/README.md's Status section for what v1 tried and why it
didn't get past Wildberries' own antibot, WBAAS): rather than crafting our own `fetch()` call with
manually-assembled headers/deviceid, navigate to the real search-results page and *intercept the
page's own network request* to the same internal search endpoint. The page's own JS is what
actually needs to satisfy WBAAS (it's the one that can execute the challenge script, since our
fetch() only ever got the challenge page back as inert text) — so let it make the call, and just
read the response it gets.
"""

import logging
import time
from urllib.parse import quote

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

SEARCH_PAGE_URL = "https://www.wildberries.ru/catalog/0/search.aspx?search={query}"
SEARCH_URL_PREFIX = "https://www.wildberries.ru/__internal/u-search/"

NAV_TIMEOUT_MS = 30_000
RESPONSE_TIMEOUT_MS = 30_000
MAX_RETRIES = 2


class WildberriesBlockedError(RuntimeError):
    """Raised when Wildberries' search API could not be reached or rejected the request."""


def _real_chrome_ua(raw_ua: str) -> str:
    return raw_ua.replace("HeadlessChrome/", "Chrome/")


class WildberriesSession:
    """A single, reusable browser session. Not thread-safe — callers must serialize access
    (see the lock in main.py), since a Playwright sync Page isn't safe for concurrent use."""

    def __init__(self) -> None:
        self._playwright = None
        self._browser = None
        self._context = None
        self._page = None

    def _launch(self) -> None:
        self._playwright = sync_playwright().start()
        self._browser = self._playwright.chromium.launch(
            headless=True,
            args=["--disable-blink-features=AutomationControlled"],
        )
        tmp_page = self._browser.new_page()
        raw_ua = tmp_page.evaluate("() => navigator.userAgent")
        tmp_page.close()

        self._context = self._browser.new_context(
            viewport={"width": 1920, "height": 1080},
            locale="ru-RU",
            user_agent=_real_chrome_ua(raw_ua),
        )

    def fetch_search(self, query: str, retries: int = MAX_RETRIES) -> dict:
        """Fetches the first page of search results as JSON, by intercepting the search-results
        page's own call to the internal search API as it renders — see module docstring."""
        for attempt in range(retries + 1):
            try:
                if self._browser is None or not self._browser.is_connected():
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
                        # WBAAS ships a behavior-tracker script — a page that never moves a
                        # mouse or scrolls looks non-human to it. Fake some minimal activity
                        # and give the challenge real wall-clock time to resolve.
                        try:
                            self._page.mouse.move(200, 300)
                            self._page.wait_for_timeout(300)
                            self._page.mouse.move(500, 450, steps=15)
                            self._page.mouse.wheel(0, 300)
                            self._page.wait_for_timeout(5_000)
                        except PlaywrightError:
                            pass
                except PlaywrightTimeoutError:
                    logging.getLogger("wildberries-scraper").warning(
                        "DEBUG no matching request seen; page title=%r; %d requests, sample: %s",
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
        for closeable in (self._context, self._browser):
            try:
                if closeable:
                    closeable.close()
            except Exception:
                pass
        try:
            if self._playwright:
                self._playwright.stop()
        except Exception:
            pass
        self._context = None
        self._browser = None
        self._playwright = None
