package me.setched.easysearch.api.infrastructure.usecase;

import me.setched.easysearch.api.application.usecase.CompareOffersUseCase;
import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.SearchQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link TimeoutEnforcingCompareOffersUseCase}'s timeout and failure-propagation behavior against
 * fake delegates.
 */
class TimeoutEnforcingCompareOffersUseCaseTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    /**
     * Verifies that a delegate finishing within the timeout returns its result unchanged.
     */
    @Test
    void returnsDelegateResultWhenWithinTimeout() {
        SearchQuery query = new SearchQuery("iphone 15");
        ComparisonResult expected = new ComparisonResult(query, List.of());
        CompareOffersUseCase fast = q -> expected;

        TimeoutEnforcingCompareOffersUseCase useCase =
                new TimeoutEnforcingCompareOffersUseCase(fast, Duration.ofSeconds(1), executor);

        assertThat(useCase.compare(query)).isEqualTo(expected);
    }

    /**
     * Verifies that a delegate exceeding the timeout causes an {@link IllegalStateException} mentioning
     * the timeout.
     */
    @Test
    void throwsWhenDelegateExceedsTimeout() {
        CompareOffersUseCase slow = query -> {
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ComparisonResult(query, List.of());
        };

        TimeoutEnforcingCompareOffersUseCase useCase =
                new TimeoutEnforcingCompareOffersUseCase(slow, Duration.ofMillis(100), executor);

        assertThatThrownBy(() -> useCase.compare(new SearchQuery("iphone 15")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out");
    }

    /**
     * Verifies that a {@link RuntimeException} thrown by the delegate (e.g. query validation) is
     * propagated as-is, not wrapped.
     */
    @Test
    void propagatesDelegateRuntimeException() {
        CompareOffersUseCase failing = query -> {
            throw new IllegalArgumentException("Search query must not be blank");
        };

        TimeoutEnforcingCompareOffersUseCase useCase =
                new TimeoutEnforcingCompareOffersUseCase(failing, Duration.ofSeconds(1), executor);

        assertThatThrownBy(() -> useCase.compare(new SearchQuery("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Search query must not be blank");
    }
}
