package me.setched.easysearch.api.application.service;

import me.setched.easysearch.api.application.usecase.CompareOffersUseCase;
import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import me.setched.easysearch.api.domain.port.SearchHistoryRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class CompareOffersService implements CompareOffersUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompareOffersService.class);

    private final List<MarketplaceClient> marketplaceClients;
    private final SearchHistoryRecorder searchHistoryRecorder;

    public CompareOffersService(List<MarketplaceClient> marketplaceClients, SearchHistoryRecorder searchHistoryRecorder) {
        this.marketplaceClients = marketplaceClients;
        this.searchHistoryRecorder = searchHistoryRecorder;
    }

    @Override
    public ComparisonResult compare(SearchQuery query) {
        if (query == null || query.query() == null || query.query().isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        List<MarketplaceOffer> offers = marketplaceClients.stream()
                .flatMap(client -> searchSafely(client, query).stream())
                .filter(Objects::nonNull)
                .toList();

        ComparisonResult result = new ComparisonResult(query, offers);
        searchHistoryRecorder.record(result);
        return result;
    }

    private List<MarketplaceOffer> searchSafely(MarketplaceClient client, SearchQuery query) {
        try {
            return client.search(query);
        } catch (Exception e) {
            log.warn("Marketplace client {} failed for query '{}': {}", client.getClass().getSimpleName(), query.query(), e.getMessage());
            return List.of();
        }
    }
}
