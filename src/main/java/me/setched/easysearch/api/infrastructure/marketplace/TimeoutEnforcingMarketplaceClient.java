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

/**
 * Decorator that enforces a timeout on any {@link MarketplaceClient}, regardless of how that client is
 * implemented internally. Centralizes timeout handling in one place instead of duplicating it per
 * integration.
 */
public class TimeoutEnforcingMarketplaceClient implements MarketplaceClient {

    private final String marketplaceName;
    private final MarketplaceClient delegate;
    private final Duration timeout;
    private final ExecutorService executor;

    /**
     * Creates a timeout-enforcing wrapper around the given client.
     *
     * @param marketplaceName a human-readable name used in logs and error messages
     * @param delegate        the client to wrap
     * @param timeout         the maximum time to wait for a search to complete
     * @param executor        the executor used to run the delegate's search
     */
    public TimeoutEnforcingMarketplaceClient(String marketplaceName, MarketplaceClient delegate, Duration timeout, ExecutorService executor) {
        this.marketplaceName = marketplaceName;
        this.delegate = delegate;
        this.timeout = timeout;
        this.executor = executor;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if the delegate times out, fails, or the wait is interrupted
     */
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

    /**
     * Returns the marketplace name, used to keep log messages meaningful even though every client is
     * wrapped in the same decorator class.
     *
     * @return the marketplace name
     */
    @Override
    public String toString() {
        return marketplaceName;
    }
}
