package me.setched.easysearch.api.infrastructure.marketplace.ozon;

import java.math.BigDecimal;
import java.util.List;

public record OzonSearchResponse(List<Item> items) {

    public record Item(String name, BigDecimal price, String url) {
    }
}
