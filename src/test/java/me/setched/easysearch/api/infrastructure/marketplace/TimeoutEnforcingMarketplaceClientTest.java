package me.setched.easysearch.api.infrastructure.marketplace;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link TimeoutEnforcingMarketplaceClient}'s timeout, failure-propagation, and pass-through
 * behavior against fake delegates.
 */
class TimeoutEnforcingMarketplaceClientTest {

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
        MarketplaceClient fast = query -> List.of(
                new MarketplaceOffer(Marketplace.OZON, "iPhone 15", new BigDecimal("74990"), "https://ozon.ru"));

        TimeoutEnforcingMarketplaceClient client =
                new TimeoutEnforcingMarketplaceClient("Ozon", fast, Duration.ofSeconds(1), executor);

        List<MarketplaceOffer> offers = client.search(new SearchQuery("iphone 15"));

        assertThat(offers).hasSize(1);
    }

    /**
     * Verifies that a delegate exceeding the timeout causes an {@link IllegalStateException} naming the
     * marketplace and mentioning the timeout.
     */
    @Test
    void throwsWhenDelegateExceedsTimeout() {
        MarketplaceClient slow = query -> {
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        };

        TimeoutEnforcingMarketplaceClient client =
                new TimeoutEnforcingMarketplaceClient("Ozon", slow, Duration.ofMillis(100), executor);

        assertThatThrownBy(() -> client.search(new SearchQuery("iphone 15")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ozon")
                .hasMessageContaining("timed out");
    }

    /**
     * Verifies that a delegate throwing an exception has that failure propagated, wrapped and named with
     * the marketplace.
     */
    @Test
    void propagatesDelegateFailure() {
        MarketplaceClient failing = query -> {
            throw new RuntimeException("marketplace unavailable");
        };

        TimeoutEnforcingMarketplaceClient client =
                new TimeoutEnforcingMarketplaceClient("Ozon", failing, Duration.ofSeconds(1), executor);

        assertThatThrownBy(() -> client.search(new SearchQuery("iphone 15")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ozon");
    }
}
