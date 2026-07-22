package me.setched.easysearch.api.web.dto;

import me.setched.easysearch.api.domain.model.ComparisonResult;

import java.util.List;

public record SearchResponse(String query, OfferResponse bestOffer, List<OfferResponse> offers, int totalOffers) {

    public static SearchResponse from(ComparisonResult result) {
        List<OfferResponse> offerResponses = result.offers().stream()
                .map(OfferResponse::from)
                .toList();

        OfferResponse bestOffer = result.bestOffer()
                .map(OfferResponse::from)
                .orElse(null);

        return new SearchResponse(result.query().query(), bestOffer, offerResponses, result.totalOffers());
    }
}
