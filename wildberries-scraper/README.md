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

**Next things to try** (not yet attempted):
- Whatever cookie/token WBAAS's challenge script sets on success is presumably required on the
  search request; the reference project's cookie-replay approach (bootstrap via Playwright, then
  curl_cffi with those cookies) might work here specifically because that bootstrap process
  happens to trigger and pass this challenge via a real navigation, where we don't. Worth
  comparing exactly which cookies the reference implementation ends up with after bootstrap
  against what we have (only `x_wbaas_token` observed so far).
- Consider whether the challenge only appears for XHR/fetch-style calls specifically (vs. how the
  page's own internal SPA code calls this same endpoint) — if so, intercepting the *page's own*
  network request (via Playwright's `page.on("response")`) after simulating a real search
  interaction, rather than issuing our own manual `fetch()`, may sidestep the challenge entirely.

If it stops working differently later:
- Check the logs first — `WildberriesBlockedError` messages now include the HTTP status and a
  truncated response body, which is usually enough to tell a WBAAS challenge apart from a
  malformed-request error or a changed response shape.
- If the request succeeds but `items` is empty, Wildberries' `products` response shape has
  likely changed — update the field paths in `_parse_products()` in `app/wildberries.py`.
