package me.setched.easysearch.api.application.service;

import me.setched.easysearch.api.application.usecase.CompareOffersUseCase;
import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import me.setched.easysearch.api.domain.port.SearchHistoryRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class CompareOffersService implements CompareOffersUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompareOffersService.class);

    private final List<MarketplaceClient> marketplaceClients;
    private final SearchHistoryRecorder searchHistoryRecorder;
    private final ExecutorService executor;

    public CompareOffersService(List<MarketplaceClient> marketplaceClients, SearchHistoryRecorder searchHistoryRecorder, ExecutorService executor) {
        this.marketplaceClients = marketplaceClients;
        this.searchHistoryRecorder = searchHistoryRecorder;
        this.executor = executor;
    }

    @Override
    public ComparisonResult compare(SearchQuery query) {
        if (query == null || query.query() == null || query.query().isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        List<CompletableFuture<List<MarketplaceOffer>>> searches = marketplaceClients.stream()
                .map(client -> CompletableFuture.supplyAsync(() -> searchSafely(client, query), executor))
                .toList();

        List<MarketplaceOffer> offers = searches.stream()
                .flatMap(search -> search.join().stream())
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
            log.warn("Marketplace client {} failed for query '{}': {}", client, query.query(), e.getMessage());
            return List.of();
        }
    }
}
