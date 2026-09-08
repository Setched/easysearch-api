"""Persistent browser session for calling Wildberries' internal search API.

Same technique as the sibling ozon-scraper (see its ozon_session.py) and adapted from the same
source, https://github.com/MaxDev43/Marketplace-Parser (parsers/wildberries.py): open ONE browser
session, let it pick up whatever session cookies/localStorage Wildberries sets on a normal page
load, then issue further requests as a JavaScript `fetch()` executed *inside* that page — so they
carry the same cookies, TLS fingerprint and headers a real browser tab would send, rather than
looking like a bot from a separate process.

The reference implementation instead closes the browser after bootstrap and replays the captured
cookies over a plain HTTP session (via curl_cffi, for TLS fingerprint impersonation). We keep the
browser open and fetch from inside it instead, matching ozon_session.py's approach — simpler (no
extra HTTP-impersonation dependency) and at least as convincing to Wildberries' antibot, since the
request is a real browser's own fetch(), not a replay.
"""

import logging
import secrets
import time
from urllib.parse import quote

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

SEARCH_PAGE_URL = "https://www.wildberries.ru/catalog/0/search.aspx?search={query}"
SEARCH_URL = "https://www.wildberries.ru/__internal/u-search/exactmatch/ru/common/v18/search"

DEVICE_ID_WAIT_MS = 8_000
NAV_TIMEOUT_MS = 30_000
MAX_RETRIES = 2

_DEVICE_ID_PRESENT_JS = """() => {
    for (let i = 0; i < localStorage.length; i++) {
        const v = localStorage.getItem(localStorage.key(i));
        if (/^site_[0-9a-f]{32}$/.test(v)) return true;
    }
    return false;
}"""

_FIND_DEVICE_ID_JS = """() => {
    for (let i = 0; i < localStorage.length; i++) {
        const v = localStorage.getItem(localStorage.key(i));
        if (/^site_[0-9a-f]{32}$/.test(v)) return v;
    }
    return null;
}"""


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

    def _goto_search_page(self, query: str) -> None:
        """Navigates to the actual search-results page for this query.

        Browsers don't let a script override the `Referer` header on `fetch()` — it's a
        "forbidden header name", set automatically from the current page's URL instead. So to get
        a Referer the search API will accept, we have to actually be on the matching search page
        before calling it, not just fetch() from wherever the session happened to load last.
        """
        if self._browser is None or not self._browser.is_connected():
            self._launch()

        self._page = self._context.new_page()
        url = SEARCH_PAGE_URL.format(query=quote(query))
        self._page.goto(url, wait_until="load", timeout=NAV_TIMEOUT_MS)
        # The SPA can keep client-side navigating/redirecting for a bit after "load" fires,
        # which destroys any in-flight evaluate()'s JS execution context. Give it a chance to
        # settle on a final page before we touch it.
        try:
            self._page.wait_for_load_state("load", timeout=5_000)
        except Exception:
            pass

    def _find_device_id(self) -> str:
        """Waits for the SPA to hydrate and write its device id to localStorage, rather than
        guessing a fixed delay — that guess was wrong (page not hydrated yet -> empty deviceid
        header -> the WAF in front of the search API 403'd every request). Falls back to a
        freshly generated id in the same format if it genuinely never appears, matching the
        reference implementation's behavior."""
        try:
            self._page.wait_for_function(_DEVICE_ID_PRESENT_JS, timeout=DEVICE_ID_WAIT_MS)
        except PlaywrightTimeoutError:
            pass
        device_id = self._page.evaluate(_FIND_DEVICE_ID_JS)
        return device_id or f"site_{secrets.token_hex(16)}"

    def fetch_search(self, query: str, retries: int = MAX_RETRIES) -> dict:
        """Fetches the first page of search results as JSON, from the matching search page."""
        for attempt in range(retries + 1):
            try:
                self._goto_search_page(query)
                device_id = self._find_device_id()

                result = self._page.evaluate(
                    """async ({url, query, deviceId}) => {
                        const params = new URLSearchParams({
                            ab_testid: "", appType: "1", curr: "rub", dest: "-1257786",
                            inheritFilters: "false", lang: "ru", page: "1", query: query,
                            resultset: "catalog", sort: "popular", spp: "30",
                            suppressSpellcheck: "false",
                        });
                        const cookieMatch = document.cookie.match(/_wbauid=([^;]+)/);
                        const wbauid = cookieMatch ? cookieMatch[1] : "";
                        const queryId = "qid" + wbauid + Date.now();
                        const r = await fetch(url + "?" + params.toString(), {
                            headers: {
                                "accept": "*/*",
                                "deviceid": deviceId,
                                "x-queryid": queryId,
                                "x-requested-with": "XMLHttpRequest",
                                "x-spa-version": "14.16.3",
                                "x-userid": "0",
                            },
                        });
                        return { status: r.status, text: await r.text() };
                    }""",
                    {"url": SEARCH_URL, "query": query, "deviceId": device_id},
                )

                if result["status"] == 200:
                    import json
                    return json.loads(result["text"])
                logging.getLogger("wildberries-scraper").warning(
                    "Wildberries search API returned HTTP %s (deviceId=%r): %.500r",
                    result["status"], device_id, result["text"],
                )
                raise WildberriesBlockedError(
                    f"Wildberries returned HTTP {result['status']} for query {query!r}")
            except (WildberriesBlockedError, PlaywrightError):
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
