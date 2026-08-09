package me.setched.easysearch.api.application.usecase;

import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.SearchQuery;

/**
 * The driving port for the application's core capability: comparing a search query across marketplaces.
 * Driving adapters (the REST controller, potentially a CLI or another protocol later) call into the
 * application through this interface without knowing how the comparison is actually carried out.
 */
public interface CompareOffersUseCase {

    /**
     * Compares the given query across all configured marketplaces.
     *
     * @param query the search query
     * @return the comparison result
     */
    ComparisonResult compare(SearchQuery query);
}
