"""Persistent, antibot-aware browser session for calling Ozon's internal page-data API.

Technique adapted from https://github.com/MaxDev43/Marketplace-Parser (parsers/ozon.py),
credited here since the approach — not just the endpoint shape — is theirs. The key insight
we were missing in an earlier attempt (plain per-request StealthyFetcher.fetch() calls): every
fresh navigation is a new chance for Ozon's antibot to reject the request. This instead opens
ONE browser session, passes the antibot challenge ONCE on the homepage, and then issues all
further requests as a JavaScript `fetch()` executed *inside* that already-trusted page — which
looks like the site's own AJAX call, not a new automated navigation.

Ozon serves a deceptive "Похоже, нет соединения" (looks like no internet connection) page as
part of its antibot challenge — this is not a real network error, see _looks_like_challenge().
"""

import json
import re
import time
from urllib.parse import quote

from playwright.sync_api import sync_playwright

HOME_URL = "https://www.ozon.ru/"
# entrypoint-api.bx is Ozon's internal page-data API — the same one www.ozon.ru's own frontend
# calls to render any page. Not officially documented or supported for third-party use.
API_URL = "https://www.ozon.ru/api/entrypoint-api.bx/page/json/v2?url="

CHALLENGE_WAIT_S = 5.0
NAV_TIMEOUT_MS = 50_000
MAX_RETRIES = 2

_CHALLENGE_TITLE_MARKERS = re.compile(r"antibot|ограничен|доступ ограничен|подтвердите|Соедине", re.I)


class OzonBlockedError(RuntimeError):
    """Raised when Ozon's antibot challenge could not be passed."""


def _real_chrome_ua(raw_ua: str) -> str:
    return raw_ua.replace("HeadlessChrome/", "Chrome/")


class OzonSession:
    """A single, reusable browser session. Not thread-safe — callers must serialize access
    (see the lock in main.py), since a Playwright sync Page isn't safe for concurrent use."""

    def __init__(self) -> None:
        self._playwright = None
        self._browser = None
        self._context = None
        self._page = None
        self._challenged = False

    def _launch(self) -> None:
        self._playwright = sync_playwright().start()
        self._browser = self._playwright.chromium.launch(
            headless=True,
            args=[
                "--disable-blink-features=AutomationControlled",
                "--mute-audio",
                "--no-first-run",
                "--no-default-browser-check",
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
        self._challenged = False

    def _ensure_challenged(self) -> None:
        if self._context is not None and self._challenged:
            return
        if self._browser is None or not self._browser.is_connected():
            self._launch()

        self._page = self._context.new_page()
        self._page.goto(HOME_URL, wait_until="domcontentloaded", timeout=NAV_TIMEOUT_MS)
        # The antibot's JS challenge needs real wall-clock time to resolve — waiting for
        # network idle isn't enough, since the challenge page itself goes network-idle fast.
        self._page.wait_for_timeout(int(CHALLENGE_WAIT_S * 1000))

        # The HTTP status of the *initial* response isn't a reliable signal by itself — a 307
        # redirect here is normal and not itself a sign of failure. What matters is what actually
        # rendered: real Ozon pages are large and mention "ozon"; the antibot's block/challenge
        # pages are short and don't.
        title = self._page.title() or ""
        try:
            content = self._page.content()
        except Exception:
            content = ""
        looks_like_ozon = "ozon" in content.lower() and len(content) > 50_000

        if not looks_like_ozon or _CHALLENGE_TITLE_MARKERS.search(title):
            raise OzonBlockedError(f"Ozon antibot challenge not passed (title: {title!r})")

        self._challenged = True

    def fetch_json(self, path: str, retries: int = MAX_RETRIES) -> dict:
        """Fetches an internal-API path as JSON, from inside the already-challenged page."""
        for attempt in range(retries + 1):
            try:
                self._ensure_challenged()
                full_url = API_URL + quote(path, safe="")
                result = self._page.evaluate(
                    """async (url) => {
                        const r = await fetch(url, { headers: { accept: "application/json" } });
                        return { status: r.status, text: await r.text() };
                    }""",
                    full_url,
                )
                if result["status"] == 200:
                    return json.loads(result["text"])
                if result["status"] not in (403, 307):
                    raise OzonBlockedError(f"Ozon returned HTTP {result['status']} for {path}")
                raise OzonBlockedError(f"HTTP {result['status']} for {path} — session likely stale")
            except OzonBlockedError:
                if attempt < retries:
                    self.shutdown()
                    time.sleep(2.0)
                    continue
                raise
        raise OzonBlockedError("fetch_json: retries exhausted")

    def shutdown(self) -> None:
        self._challenged = False
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
