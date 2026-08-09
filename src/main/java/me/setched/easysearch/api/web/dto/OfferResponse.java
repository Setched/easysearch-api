package me.setched.easysearch.api.web.dto;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;

import java.math.BigDecimal;

/**
 * Wire representation of a {@link MarketplaceOffer} in an HTTP response.
 *
 * @param marketplace the marketplace this offer came from
 * @param title       the product title
 * @param price       the offer price
 * @param url         a link to the product page
 */
public record OfferResponse(Marketplace marketplace, String title, BigDecimal price, String url) {

    /**
     * Converts a domain offer into its wire representation.
     *
     * @param offer the domain offer to convert
     * @return the corresponding response DTO
     */
    public static OfferResponse from(MarketplaceOffer offer) {
        return new OfferResponse(offer.marketplace(), offer.title(), offer.price(), offer.url());
    }
}
