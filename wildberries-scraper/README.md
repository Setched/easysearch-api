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
this service → real wildberries.ru → real offers back. Observed latency 8-20s per search
(`search-timeout`/`compare-timeout` in `application.yaml` sized accordingly).

### What makes it work

Wildberries' antibot (WBAAS) fingerprints at the browser level, not just JS-visible properties —
plain headless Playwright, however patched with JS-level stealth tricks, was reliably detected
(see "Dead ends" below). The combination that actually passes it, every part load-bearing:

1. **[`patchright`](https://github.com/Kaliiiiiiiiii-Vinyzu/patchright-python) instead of plain
   `playwright`** — a maintained fork patching Chromium's DevTools-protocol-level automation
   "tells" (e.g. the `Runtime.enable` CDP leak), not just JS properties. Drop-in replacement:
   `from patchright.sync_api import ...`.
2. **Headful real Chrome, not headless Chromium** — `channel="chrome"`, `headless=False`, run via
   Xvfb in the Dockerfile. Patchright's own docs recommend this as the least detectable setup.
3. **No manual fingerprint faking on top** — no custom User-Agent, no hand-rolled stealth init
   script. Patchright's docs warn that faking properties on top of its own patches makes the
   fingerprint *more* inconsistent, not less.
4. `launch_persistent_context()` with a real on-disk Chrome profile dir (`PROFILE_DIR`), not an
   ephemeral incognito-style context — same best-practice docs.

The request itself is made by navigating to the real search-results page and intercepting *its
own* call to the internal search API (`page.expect_response()`), rather than crafting our own
`fetch()` — the page's own JS is what needs to satisfy WBAAS, not a manual replay of its request.

**A separate, real bug found and fixed along the way:** retrying inside `fetch_search()` could
crash with `greenlet.error: Cannot switch to a different thread` — Playwright/patchright's sync
API is bound to whichever OS thread first started it, but FastAPI can dispatch different requests
sharing the same long-lived `WildberriesSession` to different threadpool threads. Fixed in
`app/main.py` by routing all session access through a dedicated single-worker
`ThreadPoolExecutor` — a lock alone prevents concurrent access but doesn't pin *which* thread runs
the code.

### Dead ends (kept short — useful if WBAAS's defenses change again)

- **Manual `fetch()` with hand-assembled headers/deviceid:** got past an initial hard block (see
  next bullet) but the search API itself always 403'd or 498'd — WBAAS serves its
  `__wbaas/challenges/antibot/...` JS challenge as the response body, which a plain `fetch()`
  can't execute.
- **Cloudflare WARP being active** looked like a much harder block than it was (instant HTTP 498
  on the very first navigation) — it auto-re-enables between machine restarts, so always check
  it's off before live-testing WB, not just once.
- **JS-level stealth (hand-rolled `navigator.*`/WebGL patches) + realistic fake mouse/scroll
  activity + up to 90s of wait**, all combined: zero effect. The page reliably cycled through the
  same ~11 WBAAS challenge URLs exactly 4 times, then gave up on its own regardless of how long we
  waited — conclusive evidence the blocker was below the JS-property layer, which is what led to
  trying patchright.
- **Running from the production server's own datacenter IP** instead of the dev machine: same
  failure pattern, ruling out IP reputation as the (sole) cause.

### If it breaks again later

- Check logs first — `WildberriesBlockedError` messages include the HTTP status and a truncated
  response body, enough to tell a WBAAS challenge apart from a malformed request or a changed
  response shape.
- If the request succeeds but `items` is empty, Wildberries' `products` JSON shape has likely
  changed — update the field paths in `_parse_products()` in `app/wildberries.py`.
- If patchright itself starts failing, check for a Chrome version mismatch first — patchright
  pins its patches to specific Chrome builds and can lag behind Google's release cadence.
