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

public class TimeoutEnforcingCompareOffersUseCase implements CompareOffersUseCase {

    private final CompareOffersUseCase delegate;
    private final Duration timeout;
    private final ExecutorService executor;

    public TimeoutEnforcingCompareOffersUseCase(CompareOffersUseCase delegate, Duration timeout, ExecutorService executor) {
        this.delegate = delegate;
        this.timeout = timeout;
        this.executor = executor;
    }

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
