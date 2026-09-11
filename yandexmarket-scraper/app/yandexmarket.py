"""Fetches Yandex Market search results.

Unlike Ozon and Wildberries, this needs no persistent browser session or antibot bypass: Yandex
Market's public search-results page (`market.yandex.ru/search`) embeds product data directly in
its server-rendered HTML as schema.org JSON-LD (`<script type="application/ld+json">`, an
`ItemList` of `Product`/`Offer` entries) — the structured data it serves to search-engine
crawlers. A plain, cookie-less HTTP GET with a normal browser User-Agent returns this data as-is;
manually verified with `curl` against the live site, no session or cookies involved. See
README.md for how this was discovered.
"""

import json
import re
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation

import httpx

SEARCH_URL = "https://market.yandex.ru/search"

_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
)

_LD_JSON_RE = re.compile(r'<script type="application/ld\+json"[^>]*>(.*?)</script>', re.S)


@dataclass
class YandexMarketItem:
    name: str
    price: Decimal
    url: str


class YandexMarketBlockedError(RuntimeError):
    """Raised when Yandex Market did not return a parseable search-results page."""


def search(query: str, client: httpx.Client) -> list[YandexMarketItem]:
    """Fetches Yandex Market search results for the given query."""
    response = client.get(
        SEARCH_URL,
        params={"text": query, "cvredirect": 1},
        headers={"User-Agent": _USER_AGENT, "Accept-Language": "ru-RU,ru;q=0.9"},
    )
    if response.status_code != 200:
        raise YandexMarketBlockedError(f"Yandex Market returned HTTP {response.status_code}")

    item_list = _find_item_list(response.text)
    if item_list is None:
        raise YandexMarketBlockedError("No ItemList JSON-LD block found in search response")

    return _parse_items(item_list)


def _find_item_list(html: str) -> dict | None:
    for match in _LD_JSON_RE.finditer(html):
        try:
            data = json.loads(match.group(1))
        except (ValueError, TypeError):
            continue
        if isinstance(data, dict) and data.get("@type") == "ItemList":
            return data
    return None


def _parse_items(item_list: dict) -> list[YandexMarketItem]:
    items: list[YandexMarketItem] = []
    for entry in item_list.get("itemListElement", []):
        product = entry.get("item") if isinstance(entry, dict) else None
        if not isinstance(product, dict):
            continue

        offers = product.get("offers")
        price = _to_decimal(offers.get("price")) if isinstance(offers, dict) else None
        name = product.get("name")
        url = product.get("url")

        if not name or not url or price is None:
            continue
        items.append(YandexMarketItem(name=name.strip(), price=price, url=url))
    return items


def _to_decimal(value: object) -> Decimal | None:
    if value is None:
        return None
    try:
        return Decimal(str(value))
    except InvalidOperation:
        return None
