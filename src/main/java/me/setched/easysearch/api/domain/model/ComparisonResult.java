package me.setched.easysearch.api.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of comparing a search query across every marketplace: all offers found, plus the derived
 * business rule of which one is cheapest.
 *
 * @param query  the query this result was produced for
 * @param offers all offers found across marketplaces, in no particular order
 */
public record ComparisonResult(SearchQuery query, List<MarketplaceOffer> offers) {

    /**
     * Returns the cheapest offer among all marketplaces, if any offers were found.
     *
     * @return the cheapest offer, or empty if {@link #offers()} is empty
     */
    public Optional<MarketplaceOffer> bestOffer() {
        return offers.stream().min(Comparator.comparing(MarketplaceOffer::price));
    }

    /**
     * Returns the total number of offers found across all marketplaces.
     *
     * @return the offer count
     */
    public int totalOffers() {
        return offers.size();
    }
}
