package me.setched.easysearch.api.infrastructure.marketplace.wildberries;

import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;

import java.util.List;

public class WildberriesMarketplaceClient implements MarketplaceClient {

    @Override
    public List<MarketplaceOffer> search(SearchQuery query) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
