# ozon-scraper

Small FastAPI service that calls Ozon's internal page-data API (`entrypoint-api.bx`) — the
same one `www.ozon.ru`'s own frontend uses to render every page — since Ozon has no public
search API. Called by `OzonMarketplaceClient` in the main Java app over HTTP — see
[OzonClientConfig.java](../src/main/java/me/setched/easysearch/api/infrastructure/marketplace/ozon/OzonClientConfig.java).

## Where this technique came from

A first attempt using [Scrapling](https://github.com/D4Vinci/Scrapling)'s `StealthyFetcher` —
opening a fresh browser navigation per request — was reliably blocked by Ozon's antibot on the
very first request, even to the homepage, even with a real installed Chrome. The approach here
is adapted from [MaxDev43/Marketplace-Parser](https://github.com/MaxDev43/Marketplace-Parser)
(`parsers/ozon.py`): open **one** browser session, pass the antibot challenge **once** on the
homepage (waiting real wall-clock time for its JS to resolve, not just network-idle), then issue
all further requests as a `fetch()` call executed *inside* that already-trusted page via
`page.evaluate()` — which looks like the site's own AJAX call rather than a new automated
navigation.

Ozon serves a deceptive "Похоже, нет соединения" (looks like no internet connection) page as
part of this challenge — that's not a real network error, see `_CHALLENGE_TITLE_MARKERS` in
`app/ozon_session.py`.

## Endpoint

`GET /search?query=<text>` → `{"items": [{"name": "...", "price": ..., "url": "..."}]}`

## Run locally

```bash
pip install -r requirements.txt
playwright install chromium
uvicorn app.main:app --reload
curl "http://localhost:8000/search?query=iphone+15"
```

The first request will be slow (~5s+, passing the antibot challenge); the session is then
reused for subsequent requests within the same process.

## Status

Verified working against the live site (manually, outside this repo's sandboxed dev environment)
— a real search returns real Ozon offers with correct name/price/url. If it stops working later
(Ozon changes things periodically):
- Check the logs first — `OzonBlockedError` means the antibot challenge itself failed (network/IP
  reputation issue — see closed PR #11 for what that looks like), not a parsing problem.
- If the challenge passes but `items` is still empty, Ozon's `tileGridDesktop` widget JSON shape
  has likely changed — update the field paths in `_parse_search_items()` in `app/ozon.py` to
  match. Log the raw JSON from `session.fetch_json()` to inspect the actual shape.
