"""Parses Ozon's search-results JSON (from OzonSession.fetch_json) into offers.

Response shape maps directly to
me.setched.easysearch.api.infrastructure.marketplace.ozon.OzonSearchResponse.
"""

import json
import re
from dataclasses import dataclass
from decimal import Decimal
from urllib.parse import urlencode

from app.ozon_session import OzonSession

SEARCH_PATH = "/search/"


@dataclass
class OzonItem:
    name: str
    price: Decimal
    url: str


def search(session: OzonSession, query: str) -> list[OzonItem]:
    """Fetches the first page of Ozon search results for the given query."""
    path = f"{SEARCH_PATH}?{urlencode({'text': query, 'from_global': 'true'})}"
    page = session.fetch_json(path)
    return _parse_search_items(page)


def _widget(page: dict, name: str) -> dict | None:
    """First widget whose key starts with `name` (e.g. "webPrice-3121879-default-1" -> "webPrice")."""
    widget_states = (page or {}).get("widgetStates", {})
    key = next((k for k in widget_states if k.split("-")[0] == name), None)
    if not key:
        return None
    try:
        return json.loads(widget_states[key])
    except (ValueError, TypeError):
        return None


def _price_to_number(text: str | None) -> Decimal | None:
    if not isinstance(text, str):
        return None
    digits = re.sub(r"[^\d]", "", text)
    return Decimal(digits) if digits else None


def _clean_url(link: str | None) -> str | None:
    if not link:
        return None
    path = str(link).split("?")[0]
    return path if path.startswith("http") else f"https://www.ozon.ru{path}"


def _parse_search_items(page: dict) -> list[OzonItem]:
    grid = _widget(page, "tileGridDesktop")
    raw_items = (grid or {}).get("items", [])

    items: list[OzonItem] = []
    for it in raw_items:
        main_state = it.get("mainState", []) if isinstance(it, dict) else []
        price_block = next((s.get("priceV2") for s in main_state if s.get("type") == "priceV2"), None)
        prices = (price_block or {}).get("price", [])
        price = _price_to_number(next((p["text"] for p in prices if p.get("textStyle") == "PRICE"), None))
        name = next((s.get("textDS", {}).get("text") for s in main_state if s.get("id") == "name"), None)
        url = _clean_url(it.get("action", {}).get("link"))

        if not name or price is None or not url:
            continue
        items.append(OzonItem(name=name.strip(), price=price, url=url))
    return items
