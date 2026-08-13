# ozon-scraper

Small FastAPI service that scrapes Ozon's public search page using [Scrapling](https://github.com/D4Vinci/Scrapling),
since Ozon has no public search API. Called by `OzonMarketplaceClient` in the main Java app over HTTP —
see [OzonClientConfig.java](../src/main/java/me/setched/easysearch/api/infrastructure/marketplace/ozon/OzonClientConfig.java).

## Endpoint

`GET /search?query=<text>` → `{"items": [{"name": "...", "price": "...", "url": "..."}]}`

## Run locally (for iterating on selectors)

```bash
pip install -r requirements.txt
scrapling install   # downloads the stealth browser Scrapling needs
uvicorn app.main:app --reload
curl "http://localhost:8000/search?query=iphone+15"
```

## Status

The scraping selectors in `app/ozon.py` are a starting point, not yet verified against the live
site. If `/search` comes back with an empty `items` list, open Ozon's search page in a real browser,
inspect a product card element, and update `SELECTOR_ITEM` / `SELECTOR_NAME` / `SELECTOR_PRICE` /
`SELECTOR_LINK` in `app/ozon.py` to match the current markup. Ozon changes this periodically, so
expect to revisit it again later even once it works.
