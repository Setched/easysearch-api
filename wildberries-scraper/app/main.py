"""FastAPI entry point: exposes Wildberries search over HTTP for the Java app to call.

Keeps ONE WildberriesSession alive across requests (see wildberries_session.py for why), the same
pattern as the sibling ozon-scraper. A Playwright sync Page isn't safe for concurrent use, so
access is serialized with a lock.
"""

import logging
import threading

from fastapi import FastAPI, Query
from fastapi.responses import JSONResponse

from app.wildberries import search as search_wildberries
from app.wildberries_session import WildberriesSession

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("wildberries-scraper")

app = FastAPI(title="wildberries-scraper")

_session = WildberriesSession()
_session_lock = threading.Lock()

# See ozon-scraper/app/main.py for why charset=utf-8 is set explicitly here.
_JSON_CONTENT_TYPE = "application/json; charset=utf-8"


@app.get("/search")
def search(query: str = Query(..., min_length=1)) -> JSONResponse:
    with _session_lock:
        try:
            items = search_wildberries(_session, query)
        except Exception:
            logger.exception("Wildberries search failed for query '%s'", query)
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
def _shutdown_session() -> None:
    _session.shutdown()
