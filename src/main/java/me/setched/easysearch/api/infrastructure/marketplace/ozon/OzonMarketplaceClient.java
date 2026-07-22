package me.setched.easysearch.api.infrastructure.marketplace.ozon;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class OzonMarketplaceClient implements MarketplaceClient {

    private static final String SUPPORTED_QUERY = "iphone 15";

    @Override
    public List<MarketplaceOffer> search(SearchQuery query) {
        if (!SUPPORTED_QUERY.equalsIgnoreCase(query.query().trim())) {
            return List.of();
        }

        return List.of(new MarketplaceOffer(
                Marketplace.OZON,
                "Apple iPhone 15 128GB",
                new BigDecimal("74990"),
                "https://www.ozon.ru/product/iphone-15"));
    }
}
