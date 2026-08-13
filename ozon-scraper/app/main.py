"""FastAPI entry point: exposes Ozon search over HTTP for the Java app to call.

Keeps ONE OzonSession alive across requests (see ozon_session.py for why — the antibot
challenge is only worth passing once, not on every request). A Playwright sync Page isn't
safe for concurrent use, so access is serialized with a lock.
"""

import logging
import threading

from fastapi import FastAPI, Query

from app.ozon import search as search_ozon
from app.ozon_session import OzonSession

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ozon-scraper")

app = FastAPI(title="ozon-scraper")

_session = OzonSession()
_session_lock = threading.Lock()


@app.get("/search")
def search(query: str = Query(..., min_length=1)) -> dict:
    with _session_lock:
        try:
            items = search_ozon(_session, query)
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


@app.on_event("shutdown")
def _shutdown_session() -> None:
    _session.shutdown()
