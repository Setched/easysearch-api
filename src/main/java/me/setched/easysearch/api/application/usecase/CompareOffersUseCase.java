package me.setched.easysearch.api.application.usecase;

import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.SearchQuery;

public interface CompareOffersUseCase {

    ComparisonResult compare(SearchQuery query);
}
