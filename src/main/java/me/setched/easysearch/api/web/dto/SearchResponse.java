package me.setched.easysearch.api.web.dto;

import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;

import java.util.Comparator;
import java.util.List;

public record SearchResponse(
        String query,
        OfferResponse bestOffer,
        List<OfferResponse> offers,
        int totalOffers,
        int page,
        int size,
        int totalPages) {

    public static SearchResponse from(ComparisonResult result, int page, int size, OfferSort sort) {
        Comparator<MarketplaceOffer> byPrice = Comparator.comparing(MarketplaceOffer::price);
        Comparator<MarketplaceOffer> comparator = sort == OfferSort.PRICE_DESC ? byPrice.reversed() : byPrice;

        List<OfferResponse> sortedOffers = result.offers().stream()
                .sorted(comparator)
                .map(OfferResponse::from)
                .toList();

        int totalOffers = sortedOffers.size();
        int totalPages = totalOffers == 0 ? 0 : (int) Math.ceil((double) totalOffers / size);
        List<OfferResponse> pageContent = paginate(sortedOffers, page, size);

        OfferResponse bestOffer = result.bestOffer()
                .map(OfferResponse::from)
                .orElse(null);

        return new SearchResponse(result.query().query(), bestOffer, pageContent, totalOffers, page, size, totalPages);
    }

    private static List<OfferResponse> paginate(List<OfferResponse> offers, int page, int size) {
        int fromIndex = Math.min(page * size, offers.size());
        int toIndex = Math.min(fromIndex + size, offers.size());
        return offers.subList(fromIndex, toIndex);
    }
}
