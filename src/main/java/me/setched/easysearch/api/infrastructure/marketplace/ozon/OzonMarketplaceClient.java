package me.setched.easysearch.api.infrastructure.marketplace.ozon;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * {@link MarketplaceClient} adapter for Ozon, backed by an HTTP call via {@link RestClient}.
 * <p>
 * <b>Known limitation:</b> this targets Ozon's Seller API ({@code api-seller.ozon.ru}), which is scoped to a
 * registered seller's own catalog and does not expose free-text search across the whole marketplace. This
 * adapter is not currently a working end-to-end integration — see project notes.
 */
public class OzonMarketplaceClient implements MarketplaceClient {

    private static final String SEARCH_PATH = "/v1/search";

    private final RestClient ozonRestClient;

    /**
     * Creates a client using the given, already-configured, Ozon REST client.
     *
     * @param ozonRestClient the configured REST client for Ozon
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
                .uri(uriBuilder -> uriBuilder.path(SEARCH_PATH).queryParam("text", query.query()).build())
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
