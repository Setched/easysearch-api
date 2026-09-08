"""Parses Wildberries' search-results JSON (from WildberriesSession.fetch_search) into offers.

Response shape maps directly to
me.setched.easysearch.api.infrastructure.marketplace.wildberries.WildberriesSearchResponse.
"""

from dataclasses import dataclass
from decimal import Decimal

from app.wildberries_session import WildberriesSession

CATALOG_URL = "https://www.wildberries.ru/catalog/{article}/detail.aspx"


@dataclass
class WildberriesItem:
    name: str
    price: Decimal
    url: str


def search(session: WildberriesSession, query: str) -> list[WildberriesItem]:
    """Fetches the first page of Wildberries search results for the given query.

    Deliberately not paginated beyond the first page, matching ozon-scraper's search() — see its
    docstring for why (no caller need for it yet; SearchQuery on the Java side has no
    pagination/limit concept).
    """
    response = session.fetch_search(query)
    return _parse_products(response)


def _parse_products(response: dict) -> list[WildberriesItem]:
    products = (response or {}).get("products", [])

    items: list[WildberriesItem] = []
    for product in products:
        article = product.get("id")
        name = product.get("name")
        sizes = product.get("sizes", [])
        price_kopecks = sizes[0].get("price", {}).get("product") if sizes else None

        if not article or not name or price_kopecks is None:
            continue

        brand = product.get("brand")
        title = f"{brand} {name}" if brand and not name.lower().startswith(str(brand).lower()) else name

        items.append(WildberriesItem(
            name=title.strip(),
            price=Decimal(price_kopecks) / 100,
            url=CATALOG_URL.format(article=article),
        ))
    return items
