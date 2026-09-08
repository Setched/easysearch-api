package me.setched.easysearch.api.infrastructure.marketplace.wildberries;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wire format of Wildberries' search response, as deserialized from JSON.
 *
 * @param items the returned product items
 */
public record WildberriesSearchResponse(List<Item> items) {

    /**
     * A single product item within a Wildberries search response.
     *
     * @param name  the product name
     * @param price the product price
     * @param url   a link to the product page
     */
    public record Item(String name, BigDecimal price, String url) {
    }
}
