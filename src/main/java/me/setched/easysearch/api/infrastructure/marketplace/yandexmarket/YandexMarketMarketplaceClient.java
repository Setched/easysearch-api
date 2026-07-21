package me.setched.easysearch.api.infrastructure.marketplace.yandexmarket;

import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;

import java.util.List;

public class YandexMarketMarketplaceClient implements MarketplaceClient {

    @Override
    public List<MarketplaceOffer> search(SearchQuery query) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
