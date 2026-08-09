package me.setched.easysearch.api.web.controller;

import jakarta.validation.Valid;
import me.setched.easysearch.api.application.usecase.CompareOffersUseCase;
import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.web.dto.SearchRequest;
import me.setched.easysearch.api.web.dto.SearchResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter exposing {@link CompareOffersUseCase} over HTTP.
 */
@RestController
public class SearchController {

    private final CompareOffersUseCase compareOffersUseCase;

    /**
     * Creates a controller backed by the given use case.
     *
     * @param compareOffersUseCase the use case to delegate to
     */
    public SearchController(CompareOffersUseCase compareOffersUseCase) {
        this.compareOffersUseCase = compareOffersUseCase;
    }

    /**
     * Compares a search query across marketplaces and returns a paginated, sorted view of the results.
     *
     * @param request the search request, including query, pagination and sort options
     * @return the paginated, sorted search response
     */
    @PostMapping("/api/search")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        SearchQuery query = new SearchQuery(request.query());
        ComparisonResult result = compareOffersUseCase.compare(query);
        return SearchResponse.from(result, request.page(), request.size(), request.sort());
    }
}
