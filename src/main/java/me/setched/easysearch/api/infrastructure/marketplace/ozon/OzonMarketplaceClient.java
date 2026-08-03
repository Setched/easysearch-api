package me.setched.easysearch.api.infrastructure.marketplace.ozon;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import org.springframework.web.client.RestClient;

import java.util.List;

public class OzonMarketplaceClient implements MarketplaceClient {

    private static final String SEARCH_PATH = "/v1/search";

    private final RestClient ozonRestClient;

    public OzonMarketplaceClient(RestClient ozonRestClient) {
        this.ozonRestClient = ozonRestClient;
    }

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
