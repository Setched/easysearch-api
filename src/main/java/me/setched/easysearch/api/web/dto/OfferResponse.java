package me.setched.easysearch.api.web.dto;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;

import java.math.BigDecimal;

public record OfferResponse(Marketplace marketplace, String title, BigDecimal price, String url) {

    public static OfferResponse from(MarketplaceOffer offer) {
        return new OfferResponse(offer.marketplace(), offer.title(), offer.price(), offer.url());
    }
}
