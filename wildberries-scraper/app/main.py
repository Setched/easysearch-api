"""FastAPI entry point: exposes Wildberries search over HTTP for the Java app to call.

Keeps ONE WildberriesSession alive across requests (see wildberries_session.py for why), the same
pattern as the sibling ozon-scraper.

Unlike ozon-scraper, all session access here is routed through a single-worker executor
(`_session_executor`) rather than just a lock. A lock alone isn't enough: Playwright/patchright's
sync API binds itself to whichever OS thread first started it, but FastAPI dispatches each
request's sync `def` handler to a thread picked from Starlette's own pool — a *different* request
can land on a different thread than the one the session was originally launched on, and touching
the same Playwright objects from that other thread crashes with
`greenlet.error: Cannot switch to a different thread`. Pinning every session call to one dedicated
thread (regardless of which pool thread the HTTP request itself landed on) avoids that.
"""

import logging
from concurrent.futures import ThreadPoolExecutor

from fastapi import FastAPI, Query
from fastapi.responses import JSONResponse

from app.wildberries import search as search_wildberries
from app.wildberries_session import WildberriesSession

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("wildberries-scraper")

app = FastAPI(title="wildberries-scraper")

_session = WildberriesSession()
_session_executor = ThreadPoolExecutor(max_workers=1)

# See ozon-scraper/app/main.py for why charset=utf-8 is set explicitly here.
_JSON_CONTENT_TYPE = "application/json; charset=utf-8"


@app.get("/search")
def search(query: str = Query(..., min_length=1)) -> JSONResponse:
    try:
        items = _session_executor.submit(search_wildberries, _session, query).result()
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
    # Must also run on the pinned session thread — see module docstring.
    _session_executor.submit(_session.shutdown).result()
    _session_executor.shutdown(wait=False)
