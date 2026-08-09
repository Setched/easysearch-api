package me.setched.easysearch.api.web.dto;

import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;

import java.util.Comparator;
import java.util.List;

/**
 * Response body for {@code POST /api/search}: a sorted, paginated view of a {@link ComparisonResult}.
 *
 * @param query       the original search query text
 * @param bestOffer   the cheapest offer found, or {@code null} if none
 * @param offers      the current page of offers, sorted as requested
 * @param totalOffers the total number of offers found, across all pages
 * @param page        the zero-based page number returned
 * @param size        the page size used
 * @param totalPages  the total number of pages available
 */
public record SearchResponse(
        String query,
        OfferResponse bestOffer,
        List<OfferResponse> offers,
        int totalOffers,
        int page,
        int size,
        int totalPages) {

    /**
     * Builds a sorted, paginated response from a domain comparison result.
     *
     * @param result the comparison result to render
     * @param page   the zero-based page number to return
     * @param size   the page size
     * @param sort   the sort order to apply to offers
     * @return the paginated, sorted response
     */
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

    /**
     * Slices a sorted offer list down to the requested page.
     *
     * @param offers the full sorted offer list
     * @param page   the zero-based page number
     * @param size   the page size
     * @return the offers belonging to the requested page
     */
    private static List<OfferResponse> paginate(List<OfferResponse> offers, int page, int size) {
        int fromIndex = Math.min(page * size, offers.size());
        int toIndex = Math.min(fromIndex + size, offers.size());
        return offers.subList(fromIndex, toIndex);
    }
}
