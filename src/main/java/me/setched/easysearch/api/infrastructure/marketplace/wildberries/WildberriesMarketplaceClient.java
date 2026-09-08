package me.setched.easysearch.api.infrastructure.marketplace.wildberries;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * {@link MarketplaceClient} adapter for Wildberries. Wildberries has no public search API — this
 * calls the sibling {@code wildberries-scraper} service (see {@code wildberries-scraper/README.md}),
 * which calls Wildberries' internal search API through a bootstrapped browser session and returns
 * results in the same shape this class expects.
 */
public class WildberriesMarketplaceClient implements MarketplaceClient {

    private static final String SEARCH_PATH = "/search";

    private final RestClient wildberriesRestClient;

    /**
     * Creates a client using the given, already-configured, REST client pointed at the
     * wildberries-scraper service.
     *
     * @param wildberriesRestClient the configured REST client for the wildberries-scraper service
     */
    public WildberriesMarketplaceClient(RestClient wildberriesRestClient) {
        this.wildberriesRestClient = wildberriesRestClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MarketplaceOffer> search(SearchQuery query) {
        WildberriesSearchResponse response = wildberriesRestClient.get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_PATH).queryParam("query", query.query()).build())
                .retrieve()
                .body(WildberriesSearchResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items().stream()
                .map(item -> new MarketplaceOffer(Marketplace.WILDBERRIES, item.name(), item.price(), item.url()))
                .toList();
    }
}
