# wildberries-scraper

Small FastAPI service that calls Wildberries' internal search API (`__internal/u-search/...`) —
the same one `www.wildberries.ru`'s own frontend uses — since Wildberries has no public search
API for third-party use. Called by `WildberriesMarketplaceClient` in the main Java app over HTTP —
see [WildberriesClientConfig.java](../src/main/java/me/setched/easysearch/api/infrastructure/marketplace/wildberries/WildberriesClientConfig.java).

## Where this technique came from

Adapted from [MaxDev43/Marketplace-Parser](https://github.com/MaxDev43/Marketplace-Parser)
(`parsers/wildberries.py`), same source credited in the sibling `ozon-scraper`. One difference:
the reference implementation bootstraps a session with Playwright, then closes the browser and
replays the captured cookies over a plain HTTP client (`curl_cffi`, for TLS fingerprint
impersonation) for actual searches. This service instead keeps the browser open and issues the
search as a `fetch()` from inside the already-loaded page — the same approach `ozon-scraper` uses
— to avoid an extra dependency and because a real browser's own fetch is at least as convincing to
an antibot as a replayed session.

## Endpoint

`GET /search?query=<text>` → `{"items": [{"name": "...", "price": ..., "url": "..."}]}`

## Run locally

```bash
pip install -r requirements.txt
playwright install chromium
uvicorn app.main:app --reload --port 8001
curl "http://localhost:8001/search?query=iphone+15"
```

## Status

**Not working yet against the live site** (per the project's own rule: don't claim a marketplace
integration works without live verification). Built by porting the reference project's technique,
but live testing surfaced a real, named antibot system gating the search API specifically —
Wildberries' own `__wbaas/challenges/antibot` (WBAAS). Findings from live debugging, in order:

1. Direct navigation to the search page with no warm-up was flat-out blocked (HTTP `498`, ~1.6KB
   of content — a block page, not a real one). Turned out to correlate with Cloudflare WARP being
   active on the dev machine — WARP's IP ranges apparently get treated as VPN traffic, which
   marketplaces are known to distrust (see the reference project's own README: "не рекомендуется
   использовать VPN"). Disabling WARP changed the failure mode entirely.
2. With WARP off: navigation succeeded, but the `deviceid` header was empty — the SPA hadn't
   finished hydrating (localStorage not yet populated) by the time a fixed delay elapsed. Fixed by
   polling for it (`page.wait_for_function`) instead of guessing a delay, with a generated
   fallback if it never appears — see `_find_device_id()` in `app/wildberries_session.py`.
3. With a real deviceid: the search API call itself now returns HTTP `498` with a **full HTML
   page** containing `<script src="/__wbaas/challenges/antibot/__static/v2/...">` — a genuine
   JS-based antibot challenge, served as the API response body. `fetch()` receives this as inert
   text; it doesn't execute the embedded script, so nothing about this challenge actually gets
   "solved" the way a real page navigation would solve it. This is the open problem.

**v2 attempt (2026-09-10, same session continued from a break):** rewrote the whole approach per
the first bullet above — instead of crafting our own `fetch()`, navigate to the real search page
and intercept *the page's own* request to the search API via `page.expect_response()`
(`app/wildberries_session.py`). Also re-confirmed and fixed an environment gotcha: Cloudflare WARP
had silently re-enabled itself (auto-starts) and needed disabling again before retesting — same
effect as before.

With that in place, live testing revealed WBAAS is considerably more sophisticated than a simple
JS-timer challenge (unlike Ozon's). Logging every request the page makes (`page.on("request")`)
showed a clear, repeating pattern — the same ~11 URLs cycling **exactly 4 times** (43 requests
total) before giving up:
```
catalog/0/search.aspx?search=... (reload)
__wbaas/challenges/antibot/__static/v2/index-*.js
__wbaas/challenges/antibot/__static/v2/browser-check.js       <- checks the browser itself
__wbaas/challenges/antibot/api/v1/find-frontend-settings
__wbaas/challenges/antibot/statics/challenge-solver_v1.0.8.js
__wbaas/challenges/antibot/statics/behavior-tracker_v1.0.3.js <- tracks mouse/scroll/etc.
__wbaas/challenges/antibot/api/v1/create-token  (x2)
```
The named files are self-explanatory: `browser-check.js` fingerprints the browser itself (likely
`navigator.webdriver`, headless-specific quirks, etc. beyond the one flag we currently disable),
and `behavior-tracker.js` watches for human-like interaction. Added a minimal fake interaction
(mouse move + scroll + a few seconds of wall-clock wait) inside the same navigation — **no
effect**, identical 4x loop and 43 requests both with and without it. So this isn't just "we never
touched the mouse" — either the fingerprint check itself fails first (most likely: our headless
Chromium probably reads as headless/automated to `browser-check.js` regardless of mouse activity),
or the behavior tracker needs a much more realistic interaction pattern than two mouse moves and a
wheel event.

**Next things to try** (not yet attempted, roughly in order of effort):
- A proper stealth-patched Chromium (e.g. a maintained `patchright`/undetected-playwright-style
  fork, or manually patching more `navigator`/`window` fingerprint surfaces than just
  `--disable-blink-features=AutomationControlled`) to see if `browser-check.js` is the actual
  blocker before investing in behavior simulation at all.
- If fingerprinting isn't the issue: more realistic, continuous interaction (small randomized
  mouse movements over several seconds, not two discrete jumps) to satisfy the behavior tracker.
- Reconsider priority: the reference project's README claims "на Wildberries анти-бот слабее"
  (Wildberries' antibot is weaker), which does not match what we're finding — WBAAS looks at least
  as sophisticated as Ozon's antibot, possibly more. Worth revisiting whether Wildberries is
  actually the easier next target compared to Yandex Market, which hasn't been investigated at all
  yet.

If it stops working differently later:
- Check the logs first — `WildberriesBlockedError` messages now include the HTTP status and a
  truncated response body, which is usually enough to tell a WBAAS challenge apart from a
  malformed-request error or a changed response shape.
- If the request succeeds but `items` is empty, Wildberries' `products` response shape has
  likely changed — update the field paths in `_parse_products()` in `app/wildberries.py`.
