# yandexmarket-scraper

Small FastAPI service that fetches Yandex Market's public search-results page
(`market.yandex.ru/search`) and reads product data straight out of it. Called by
`YandexMarketMarketplaceClient` in the main Java app over HTTP — see
[YandexMarketClientConfig.java](../src/main/java/me/setched/easysearch/api/infrastructure/marketplace/yandexmarket/YandexMarketClientConfig.java).

## Where this technique came from

Before writing any code, the search-results page was opened manually and inspected: no antibot
challenge, no login wall — a plain `curl` with just a normal browser `User-Agent`, no cookies, no
session, returned the same product data as a real browser. The page embeds it as schema.org
structured data (`<script type="application/ld+json">`, an `ItemList` of `Product`/`Offer`
entries) — the same markup sites serve to search-engine crawlers, so it's meant to be read by
automated clients and is unlikely to disappear or need JS to render.

This is a different situation from Ozon and Wildberries, both of which reliably blocked even a
single plain request and needed a persistent, antibot-challenged browser session
(see `ozon-scraper/README.md` and `wildberries-scraper/README.md`). Confirmed here the other way
round: don't reach for a browser until a plain HTTP request has actually been tried and blocked.

## Endpoint

`GET /search?query=<text>` → `{"items": [{"name": "...", "price": ..., "url": "..."}]}`

## Run locally

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8002
curl "http://localhost:8002/search?query=iphone+15"
```

No browser install step, no `shm_size` tuning in Docker, no antibot warm-up latency — every
request is independent.

## Status

Verified working against the live site (manually, outside this repo's sandboxed dev environment)
— a real search returns real Yandex Market offers with correct name/price/url. If it stops
working later (Yandex changes things periodically):
- Check the logs first — `YandexMarketBlockedError` means either a non-200 response or no
  `ItemList` JSON-LD block was found in the page — likely Yandex started serving a challenge page
  or changed the search page's markup.
- If the request succeeds but the JSON-LD's `ItemList` is present with a different shape, update
  the field paths in `_parse_items()` in `app/yandexmarket.py`.
- If Yandex starts blocking plain HTTP requests (antibot, rate limiting, IP reputation), this
  approach stops being viable and the technique would need to become browser-based, like
  `ozon-scraper`/`wildberries-scraper` — don't assume that in advance, confirm it first.
