package me.setched.easysearch.api.infrastructure.marketplace.yandexmarket;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wire format of the yandexmarket-scraper's search response, as deserialized from JSON.
 *
 * @param items the returned product items
 */
public record YandexMarketSearchResponse(List<Item> items) {

    /**
     * A single product item within a Yandex Market search response.
     *
     * @param name  the product name
     * @param price the product price
     * @param url   a link to the product page
     */
    public record Item(String name, BigDecimal price, String url) {
    }
}
