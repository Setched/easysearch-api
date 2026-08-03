package me.setched.easysearch.api.infrastructure.marketplace;

import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimeoutEnforcingMarketplaceClient implements MarketplaceClient {

    private final String marketplaceName;
    private final MarketplaceClient delegate;
    private final Duration timeout;
    private final ExecutorService executor;

    public TimeoutEnforcingMarketplaceClient(String marketplaceName, MarketplaceClient delegate, Duration timeout, ExecutorService executor) {
        this.marketplaceName = marketplaceName;
        this.delegate = delegate;
        this.timeout = timeout;
        this.executor = executor;
    }

    @Override
    public List<MarketplaceOffer> search(SearchQuery query) {
        Future<List<MarketplaceOffer>> future = executor.submit(() -> delegate.search(query));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException(marketplaceName + " timed out after " + timeout, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(marketplaceName + " search failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(marketplaceName + " search interrupted", e);
        }
    }

    @Override
    public String toString() {
        return marketplaceName;
    }
}
