package me.setched.easysearch.api.domain.port;

import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;

import java.util.List;

/**
 * A port describing what the domain needs from a marketplace: the ability to search it for offers matching
 * a query. Implementations (adapters) live in the infrastructure layer and decide how the search is actually
 * performed (HTTP call, scraping, a hardcoded stub, etc.) — the domain and application layers only ever see
 * this contract.
 */
public interface MarketplaceClient {

    /**
     * Searches this marketplace for offers matching the given query.
     *
     * @param query the search query
     * @return matching offers, or an empty list if none were found
     */
    List<MarketplaceOffer> search(SearchQuery query);
}
