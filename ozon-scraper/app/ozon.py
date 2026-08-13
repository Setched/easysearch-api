"""Scrapes Ozon's public search results page for product offers.

Ozon's search page is a JS-rendered SPA, so a plain HTTP fetch won't see product
data — this uses Scrapling's StealthyFetcher, which renders the page in a
stealth-patched browser before we parse it.

NOT YET VERIFIED AGAINST THE LIVE SITE. The CSS selectors below are a starting
point based on Ozon's search-result markup at the time of writing; Ozon changes
its markup often, so if this returns no items, open the search page in a
browser, inspect a product card, and update SELECTOR_* below to match.
"""

from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from typing import List

from scrapling.fetchers import StealthyFetcher

SEARCH_URL = "https://www.ozon.ru/search/"

# Selector for a single product card within the search results grid.
SELECTOR_ITEM = '[data-widget="searchResultsV2"] .tile-root'
# Selectors are relative to a product card.
SELECTOR_NAME = 'span.tsBody500Medium::text'
SELECTOR_PRICE = 'span.tsHeadline500Medium::text'
SELECTOR_LINK = 'a::attr(href)'


@dataclass
class OzonItem:
    name: str
    price: Decimal
    url: str


def search(query: str) -> List[OzonItem]:
    """Fetches Ozon's search results page for the given query and extracts offers."""
    page = StealthyFetcher.fetch(SEARCH_URL, params={"text": query}, headless=True, network_idle=True)
    return _parse(page)


def _parse(page) -> List[OzonItem]:
    items: List[OzonItem] = []
    for card in page.css(SELECTOR_ITEM):
        name = card.css_first(SELECTOR_NAME)
        price = card.css_first(SELECTOR_PRICE)
        href = card.css_first(SELECTOR_LINK)
        if not name or not price or not href:
            continue

        price_value = _parse_price(price)
        if price_value is None:
            continue

        items.append(OzonItem(name=name.strip(), price=price_value, url=_absolute_url(href)))
    return items


def _parse_price(raw: str) -> Decimal | None:
    digits = "".join(ch for ch in raw if ch.isdigit())
    if not digits:
        return None
    try:
        return Decimal(digits)
    except InvalidOperation:
        return None


def _absolute_url(href: str) -> str:
    return href if href.startswith("http") else f"https://www.ozon.ru{href}"
