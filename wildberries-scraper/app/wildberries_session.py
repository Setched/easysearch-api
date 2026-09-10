"""Persistent browser session for calling Wildberries' internal search API.

v3 approach (see wildberries-scraper/README.md's Status section for the full history): v2's
network-interception idea (navigate to the search page, capture the page's own request to the
internal API rather than crafting our own `fetch()`) is kept, combined with two more things v1/v2
didn't have, since live testing showed Wildberries' antibot (WBAAS) does real browser
fingerprinting (`browser-check.js`) and behavioral tracking (`behavior-tracker.js`), not just a
timer-based challenge like Ozon's:

1. A stealth init script (`_STEALTH_INIT_SCRIPT`) patching the handful of automation "tells" that
   naive headless Chromium exposes by default (`navigator.webdriver`, missing `window.chrome`,
   empty `navigator.plugins`, a headless-only WebGL renderer string, etc.) — the same core patch
   set popularized by `puppeteer-extra-plugin-stealth`/`playwright-stealth`, applied by hand via
   `context.add_init_script()` rather than pulling in an extra (largely unmaintained) dependency.
2. More realistic fake interaction (`_simulate_human_activity()`): several small, randomized mouse
   movements and scrolls spread over a few seconds, instead of two discrete jumps — a page that
   teleports the cursor twice and never scrolls is itself a signal to a behavior tracker.
"""

import logging
import random
import time
from urllib.parse import quote

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import Page
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

SEARCH_PAGE_URL = "https://www.wildberries.ru/catalog/0/search.aspx?search={query}"
SEARCH_URL_PREFIX = "https://www.wildberries.ru/__internal/u-search/"

NAV_TIMEOUT_MS = 30_000
RESPONSE_TIMEOUT_MS = 90_000
MAX_RETRIES = 2

# Core "undetected browser" patch set — same handful of checks most bot-fingerprinting scripts
# run, popularized by puppeteer-extra-plugin-stealth. Applied once per browser context via
# add_init_script(), so it runs before any of the page's own JS on every navigation.
_STEALTH_INIT_SCRIPT = """
Object.defineProperty(Navigator.prototype, 'webdriver', { get: () => undefined });

window.chrome = window.chrome || { runtime: {}, loadTimes: function () {}, csi: function () {}, app: {} };

const originalQuery = window.navigator.permissions.query;
window.navigator.permissions.query = (parameters) => (
    parameters.name === 'notifications'
        ? Promise.resolve({ state: Notification.permission })
        : originalQuery(parameters)
);

Object.defineProperty(navigator, 'plugins', {
    get: () => [1, 2, 3, 4, 5].map(() => ({ name: 'Chrome PDF Plugin' })),
});

Object.defineProperty(navigator, 'languages', { get: () => ['ru-RU', 'ru', 'en-US', 'en'] });

const getParameter = WebGLRenderingContext.prototype.getParameter;
WebGLRenderingContext.prototype.getParameter = function (parameter) {
    if (parameter === 37445) return 'Intel Inc.';
    if (parameter === 37446) return 'Intel Iris OpenGL Engine';
    return getParameter.apply(this, arguments);
};
"""


class WildberriesBlockedError(RuntimeError):
    """Raised when Wildberries' search API could not be reached or rejected the request."""


def _real_chrome_ua(raw_ua: str) -> str:
    return raw_ua.replace("HeadlessChrome/", "Chrome/")


def _simulate_human_activity(page: Page) -> None:
    """Fakes a few seconds of plausible mouse/scroll activity for WBAAS's behavior tracker —
    two discrete mouse teleports (what v2 did) look at least as robotic as no movement at all."""
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
            args=[
                "--disable-blink-features=AutomationControlled",
                "--disable-infobars",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-dev-shm-usage",
            ],
        )
        tmp_page = self._browser.new_page()
        raw_ua = tmp_page.evaluate("() => navigator.userAgent")
        tmp_page.close()

        self._context = self._browser.new_context(
            viewport={"width": 1920, "height": 1080},
            locale="ru-RU",
            user_agent=_real_chrome_ua(raw_ua),
        )
        self._context.add_init_script(_STEALTH_INIT_SCRIPT)

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
                        _simulate_human_activity(self._page)
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
