# wildberries-scraper

Small FastAPI service that calls Wildberries' internal search API (`__internal/u-search/...`) —
the same one `www.wildberries.ru`'s own frontend uses — since Wildberries has no public search
API for third-party use. Called by `WildberriesMarketplaceClient` in the main Java app over HTTP —
see [WildberriesClientConfig.java](../src/main/java/me/setched/easysearch/api/infrastructure/marketplace/wildberries/WildberriesClientConfig.java).

## Where this technique came from

Adapted from [MaxDev43/Marketplace-Parser](https://github.com/MaxDev43/Marketplace-Parser)
(`parsers/wildberries.py`), same source credited in the sibling `ozon-scraper`, though the working
approach here ended up diverging from it substantially — see Status below for why.

## Endpoint

`GET /search?query=<text>` → `{"items": [{"name": "...", "price": ..., "url": "..."}]}`

## Run locally

```bash
pip install -r requirements.txt
patchright install --with-deps chrome
uvicorn app.main:app --reload --port 8001
curl "http://localhost:8001/search?query=iphone+15"
```

Needs an X display to run headful outside Docker (see `Dockerfile` for how the container provides
one via Xvfb) — on a normal desktop this isn't an issue, it'll just open a visible Chrome window.

## Status

**Working, live-verified end-to-end** (2026-09-10) — confirmed through the full chain: Java app →
this service → real wildberries.ru → real offers back. Getting here took four attempts across two
sessions; the full history is kept below because it explains *why* the final approach looks the
way it does, and will matter again if Wildberries changes its defenses.

### What finally worked (v4)

Wildberries' antibot (WBAAS, see v1-v3 below) turned out to require going a level deeper than
JS-visible browser properties. The winning combination, all three parts load-bearing:

1. **[`patchright`](https://github.com/Kaliiiiiiiiii-Vinyzu/patchright-python) instead of plain
   `playwright`** — a maintained fork that patches Chromium's own DevTools-protocol-level
   automation "tells" (e.g. the `Runtime.enable` CDP leak), not just JS properties. Drop-in
   replacement: same API, just `from patchright.sync_api import ...`.
2. **Headful real Chrome, not headless Chromium** — `channel="chrome"`,
   `headless=False`, run via Xvfb in the Dockerfile (`app/wildberries_session.py` no longer
   launches headless at all). Patchright's own docs recommend this as the least detectable
   configuration; headless mode has quirks no JS patching removes.
3. **No manual fingerprint faking** — the v3 attempt's hand-rolled stealth init script and
   User-Agent override are both gone. Patchright's docs explicitly warn that faking properties on
   top of its own patches makes the fingerprint look *more* inconsistent, not less — once
   patchright + headful Chrome is doing the work, added fakery only hurts.

Also switched `launch()` + `new_context()` to `launch_persistent_context()` with a real on-disk
Chrome profile directory (`PROFILE_DIR`), per the same best-practice docs.

**Observed live latency:** 8-20 seconds per search (varies by query/run) — well inside the
timeouts (raised to 45s/50s in `application.yaml` to give headroom; the previous 20s/25s, sized
for Ozon, were too tight once actually measured against Wildberries).

**A real, separate bug found and fixed along the way:** retrying inside `fetch_search()`
(re-launching the session) could crash with `greenlet.error: Cannot switch to a different thread`
— Playwright/patchright's sync API is bound to whichever OS thread first started it, but FastAPI
dispatches each request to a thread from Starlette's own pool, which can differ between requests
sharing the same long-lived `WildberriesSession`. Fixed in `app/main.py` by routing all session
access through a dedicated single-worker `ThreadPoolExecutor` instead of just a lock — a lock
prevents concurrent access but doesn't pin *which* thread runs the code.

### v1-v3: what didn't work, and why it's still useful to know

Findings from live debugging, in order:

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

**v3 attempt (same session, immediately after v2):** combined both remaining ideas — a hand-rolled
stealth init script (`_STEALTH_INIT_SCRIPT` in `wildberries_session.py`, the standard
`puppeteer-extra-plugin-stealth` core patch set: fakes `navigator.webdriver`, `window.chrome`,
`navigator.plugins`, WebGL vendor/renderer strings, etc.) plus a much more realistic
`_simulate_human_activity()` (randomized multi-step mouse movement and scrolling over several
seconds instead of two discrete jumps). Also bumped the response-wait timeout from 30s to 90s, in
case the challenge just needed more real wall-clock time (the way Ozon's does, just more of it).

**Result: no change at all**, and this is the most conclusive finding yet. Across three separate
runs — stealth+behavior-sim at 30s, then again at 90s — the request log is **byte-for-byte
identical every time**: the same ~11 URLs cycling through exactly **4 attempts** (confirmed by
counting page reloads in the 90s run: 12 total across 3 retries = 4 per attempt), then the page
just stops issuing requests on its own, well before any of our timeouts would even matter. This
rules out "not enough real time" as the bottleneck — WBAAS's own client-side logic gives up after
a fixed 4 attempts regardless of how long we're willing to wait afterward, and neither the stealth
patches nor the fake interaction changed that outcome even slightly.

**Conclusion at the time (proven wrong by v4, kept for context):** we thought the complete lack of
variation across raw fetch, network interception, +stealth, and +longer timeout meant the failing
signal was unreachable by hand-rolled patches. That was half right — it *was* below the JS
property layer — but "unreachable" turned out to mean "needs patchright's CDP-level patches +
headful Chrome," not "needs a residential proxy or reverse-engineering the challenge script." The
IP-reputation angle specifically was tested (see the "server IP" experiment) and ruled out: running
from the production VPS's datacenter IP produced the identical failure pattern as the dev machine.

If it stops working differently later:
- Check the logs first — `WildberriesBlockedError` messages now include the HTTP status and a
  truncated response body, which is usually enough to tell a WBAAS challenge apart from a
  malformed-request error or a changed response shape.
- If the request succeeds but `items` is empty, Wildberries' `products` response shape has
  likely changed — update the field paths in `_parse_products()` in `app/wildberries.py`.
