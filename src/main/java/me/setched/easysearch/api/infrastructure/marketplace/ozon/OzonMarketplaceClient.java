package me.setched.easysearch.api.infrastructure.marketplace.ozon;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * {@link MarketplaceClient} adapter for Ozon. Ozon has no public search API — this calls the sibling
 * {@code ozon-scraper} service (see {@code ozon-scraper/README.md}), which calls Ozon's internal
 * page-data API through a persistent, antibot-challenged browser session and returns results in the
 * same shape this class used to expect from a real API.
 */
public class OzonMarketplaceClient implements MarketplaceClient {

    private static final String SEARCH_PATH = "/search";

    private final RestClient ozonRestClient;

    /**
     * Creates a client using the given, already-configured, REST client pointed at the ozon-scraper
     * service.
     *
     * @param ozonRestClient the configured REST client for the ozon-scraper service
     */
    public OzonMarketplaceClient(RestClient ozonRestClient) {
        this.ozonRestClient = ozonRestClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MarketplaceOffer> search(SearchQuery query) {
        OzonSearchResponse response = ozonRestClient.get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_PATH).queryParam("query", query.query()).build())
                .retrieve()
                .body(OzonSearchResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items().stream()
                .map(item -> new MarketplaceOffer(Marketplace.OZON, item.name(), item.price(), item.url()))
                .toList();
    }
}
