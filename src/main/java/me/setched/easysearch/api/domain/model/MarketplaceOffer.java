package me.setched.easysearch.api.domain.model;

import java.math.BigDecimal;

public record MarketplaceOffer(Marketplace marketplace, String title, BigDecimal price, String url) {
}
