package me.setched.easysearch.api.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

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
