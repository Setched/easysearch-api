package me.setched.easysearch.api.domain.model;

import java.util.List;

public record ComparisonResult(SearchQuery query, List<MarketplaceOffer> offers) {
}
