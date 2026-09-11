"""FastAPI entry point: exposes Yandex Market search over HTTP for the Java app to call.

No persistent session/lock needed here, unlike the sibling ozon-scraper and wildberries-scraper —
each request is an independent, stateless HTTP call (see app/yandexmarket.py for why no antibot
bypass is required), so a single shared httpx.Client (safe for concurrent use) is enough.
"""

import logging

import httpx
from fastapi import FastAPI, Query
from fastapi.responses import JSONResponse

from app.yandexmarket import search as search_yandexmarket

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("yandexmarket-scraper")

app = FastAPI(title="yandexmarket-scraper")

_client = httpx.Client(timeout=10.0)

# See ozon-scraper/app/main.py for why charset=utf-8 is set explicitly here.
_JSON_CONTENT_TYPE = "application/json; charset=utf-8"


@app.get("/search")
def search(query: str = Query(..., min_length=1)) -> JSONResponse:
    try:
        items = search_yandexmarket(query, _client)
    except Exception:
        logger.exception("Yandex Market search failed for query '%s'", query)
        return JSONResponse(content={"items": []}, media_type=_JSON_CONTENT_TYPE)

    return JSONResponse(
        content={
            "items": [
                {"name": item.name, "price": float(item.price), "url": item.url}
                for item in items
            ]
        },
        media_type=_JSON_CONTENT_TYPE,
    )


@app.get("/health")
def health() -> JSONResponse:
    return JSONResponse(content={"status": "ok"}, media_type=_JSON_CONTENT_TYPE)


@app.on_event("shutdown")
def _shutdown_client() -> None:
    _client.close()
