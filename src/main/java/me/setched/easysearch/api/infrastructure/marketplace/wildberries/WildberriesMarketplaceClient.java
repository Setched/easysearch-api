package me.setched.easysearch.api.infrastructure.marketplace.wildberries;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;

import java.math.BigDecimal;
import java.util.List;

public class WildberriesMarketplaceClient implements MarketplaceClient {

    private static final String SUPPORTED_QUERY = "iphone 15";

    @Override
    public List<MarketplaceOffer> search(SearchQuery query) {
        if (!SUPPORTED_QUERY.equalsIgnoreCase(query.query().trim())) {
            return List.of();
        }

        return List.of(new MarketplaceOffer(
                Marketplace.WILDBERRIES,
                "Apple iPhone 15 128GB",
                new BigDecimal("72990"),
                "https://www.wildberries.ru/catalog/iphone-15"));
    }
}
