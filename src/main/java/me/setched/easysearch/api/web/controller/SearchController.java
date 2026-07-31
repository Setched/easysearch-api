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

@RestController
public class SearchController {

    private final CompareOffersUseCase compareOffersUseCase;

    public SearchController(CompareOffersUseCase compareOffersUseCase) {
        this.compareOffersUseCase = compareOffersUseCase;
    }

    @PostMapping("/api/search")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        SearchQuery query = new SearchQuery(request.query());
        ComparisonResult result = compareOffersUseCase.compare(query);
        return SearchResponse.from(result, request.page(), request.size(), request.sort());
    }
}
