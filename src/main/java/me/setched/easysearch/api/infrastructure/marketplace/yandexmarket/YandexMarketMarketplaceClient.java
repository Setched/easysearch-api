package me.setched.easysearch.api.infrastructure.marketplace.yandexmarket;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * {@link MarketplaceClient} adapter for Yandex Market. Yandex Market has no public search API — this calls
 * the sibling {@code yandexmarket-scraper} service (see {@code yandexmarket-scraper/README.md}), which reads
 * product data directly out of Yandex Market's public search-results page and returns results in the same
 * shape this class expects.
 */
public class YandexMarketMarketplaceClient implements MarketplaceClient {

    private static final String SEARCH_PATH = "/search";

    private final RestClient yandexMarketRestClient;

    /**
     * Creates a client using the given, already-configured, REST client pointed at the yandexmarket-scraper
     * service.
     *
     * @param yandexMarketRestClient the configured REST client for the yandexmarket-scraper service
     */
    public YandexMarketMarketplaceClient(RestClient yandexMarketRestClient) {
        this.yandexMarketRestClient = yandexMarketRestClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MarketplaceOffer> search(SearchQuery query) {
        YandexMarketSearchResponse response = yandexMarketRestClient.get()
                .uri(uriBuilder -> uriBuilder.path(SEARCH_PATH).queryParam("query", query.query()).build())
                .retrieve()
                .body(YandexMarketSearchResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items().stream()
                .map(item -> new MarketplaceOffer(Marketplace.YANDEX_MARKET, item.name(), item.price(), item.url()))
                .toList();
    }
}
