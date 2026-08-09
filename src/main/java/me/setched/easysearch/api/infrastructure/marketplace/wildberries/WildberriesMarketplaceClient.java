package me.setched.easysearch.api.infrastructure.marketplace.wildberries;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link MarketplaceClient} stub for Wildberries.
 * <p>
 * <b>Known limitation:</b> this is a hardcoded placeholder, not a real integration — it only ever returns a
 * single canned offer for the exact query {@code "iphone 15"} and an empty list otherwise. A real HTTP-based
 * (or scraping-based) implementation is still to be built.
 */
public class WildberriesMarketplaceClient implements MarketplaceClient {

    private static final String SUPPORTED_QUERY = "iphone 15";

    /**
     * {@inheritDoc}
     */
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
