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

/**
 * Orchestrates {@link CompareOffersUseCase}: queries every configured marketplace client in parallel,
 * isolates individual client failures so one bad marketplace doesn't spoil the rest, and records the
 * outcome. Contains no business rules of its own — those belong to {@link ComparisonResult}.
 */
public class CompareOffersService implements CompareOffersUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompareOffersService.class);

    private final List<MarketplaceClient> marketplaceClients;
    private final SearchHistoryRecorder searchHistoryRecorder;
    private final ExecutorService executor;

    /**
     * Creates a service that queries the given clients.
     *
     * @param marketplaceClients    the marketplace clients to query
     * @param searchHistoryRecorder where to record comparison outcomes
     * @param executor              executor used to run marketplace searches concurrently
     */
    public CompareOffersService(List<MarketplaceClient> marketplaceClients, SearchHistoryRecorder searchHistoryRecorder, ExecutorService executor) {
        this.marketplaceClients = marketplaceClients;
        this.searchHistoryRecorder = searchHistoryRecorder;
        this.executor = executor;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Queries all marketplace clients concurrently, waits for every one of them to finish or fail, and
     * records the combined result before returning it.
     *
     * @throws IllegalArgumentException if the query is null or blank
     */
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

    /**
     * Runs a single client's search, swallowing any exception so a single failing marketplace can't fail
     * the whole comparison.
     *
     * @param client the client to query
     * @param query  the search query
     * @return the client's offers, or an empty list if it failed
     */
    private List<MarketplaceOffer> searchSafely(MarketplaceClient client, SearchQuery query) {
        try {
            return client.search(query);
        } catch (Exception e) {
            log.warn("Marketplace client {} failed for query '{}': {}", client, query.query(), e.getMessage());
            return List.of();
        }
    }
}
