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

class TimeoutEnforcingMarketplaceClientTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void returnsDelegateResultWhenWithinTimeout() {
        MarketplaceClient fast = query -> List.of(
                new MarketplaceOffer(Marketplace.OZON, "iPhone 15", new BigDecimal("74990"), "https://ozon.ru"));

        TimeoutEnforcingMarketplaceClient client =
                new TimeoutEnforcingMarketplaceClient("Ozon", fast, Duration.ofSeconds(1), executor);

        List<MarketplaceOffer> offers = client.search(new SearchQuery("iphone 15"));

        assertThat(offers).hasSize(1);
    }

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
