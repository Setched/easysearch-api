package me.setched.easysearch.api.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record ComparisonResult(SearchQuery query, List<MarketplaceOffer> offers) {

    public Optional<MarketplaceOffer> bestOffer() {
        return offers.stream().min(Comparator.comparing(MarketplaceOffer::price));
    }

    public int totalOffers() {
        return offers.size();
    }
}
