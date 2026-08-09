package me.setched.easysearch.api.domain.model;

import java.math.BigDecimal;

/**
 * A single product offer returned by a marketplace for a search query.
 *
 * @param marketplace the marketplace this offer came from
 * @param title       the product title as returned by the marketplace
 * @param price       the offer price
 * @param url         a link to the product page
 */
public record MarketplaceOffer(Marketplace marketplace, String title, BigDecimal price, String url) {
}
