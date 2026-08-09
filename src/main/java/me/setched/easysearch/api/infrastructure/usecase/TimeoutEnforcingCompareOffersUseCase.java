package me.setched.easysearch.api.infrastructure.usecase;

import me.setched.easysearch.api.application.usecase.CompareOffersUseCase;
import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.SearchQuery;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Decorator that enforces an overall timeout on the whole {@link CompareOffersUseCase}. Even though
 * individual marketplace clients are queried in parallel and each has its own timeout, this provides a
 * hard ceiling on total request latency in case something misbehaves beyond that.
 */
public class TimeoutEnforcingCompareOffersUseCase implements CompareOffersUseCase {

    private final CompareOffersUseCase delegate;
    private final Duration timeout;
    private final ExecutorService executor;

    /**
     * Creates a timeout-enforcing wrapper around the given use case.
     *
     * @param delegate the use case to wrap
     * @param timeout  the maximum time to wait for the comparison to complete
     * @param executor the executor used to run the delegate
     */
    public TimeoutEnforcingCompareOffersUseCase(CompareOffersUseCase delegate, Duration timeout, ExecutorService executor) {
        this.delegate = delegate;
        this.timeout = timeout;
        this.executor = executor;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException   if the comparison times out, the wait is interrupted, or the delegate
     *                                  fails with a non-{@link RuntimeException} cause
     * @throws IllegalArgumentException if the delegate rejects the query (propagated as-is)
     */
    @Override
    public ComparisonResult compare(SearchQuery query) {
        Future<ComparisonResult> future = executor.submit(() -> delegate.compare(query));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("Comparison timed out after " + timeout, e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Comparison failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Comparison interrupted", e);
        }
    }
}
