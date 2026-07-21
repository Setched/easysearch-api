package me.setched.easysearch.api.domain.port;

import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;

import java.util.List;

public interface MarketplaceClient {

    List<MarketplaceOffer> search(SearchQuery query);
}
