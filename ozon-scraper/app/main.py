"""FastAPI entry point: exposes Ozon search over HTTP for the Java app to call.

Response shape matches me.setched.easysearch.api.infrastructure.marketplace.ozon.OzonSearchResponse
exactly, so the Java-side adapter needs no parsing changes beyond the base URL.
"""

import logging

from fastapi import FastAPI, Query

from app.ozon import search as search_ozon

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ozon-scraper")

app = FastAPI(title="ozon-scraper")


@app.get("/search")
def search(query: str = Query(..., min_length=1)) -> dict:
    try:
        items = search_ozon(query)
    except Exception:
        logger.exception("Ozon search failed for query '%s'", query)
        return {"items": []}

    return {
        "items": [
            {"name": item.name, "price": float(item.price), "url": item.url}
            for item in items
        ]
    }


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
