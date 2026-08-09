package me.setched.easysearch.api.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/search}.
 *
 * @param query the search query text; must not be blank
 * @param page  zero-based page number; defaults to 0 if omitted
 * @param size  page size, between 1 and 100; defaults to 20 if omitted
 * @param sort  sort order for offers; defaults to {@link OfferSort#PRICE_ASC} if omitted
 */
public record SearchRequest(
        @NotBlank String query,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        OfferSort sort) {

    public SearchRequest {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = 20;
        }
        if (sort == null) {
            sort = OfferSort.PRICE_ASC;
        }
    }
}
