package me.setched.easysearch.api.application.service;

import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import me.setched.easysearch.api.domain.port.SearchHistoryRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies {@link CompareOffersService}'s orchestration: aggregating offers, picking the cheapest,
 * isolating failing clients, recording history, and querying clients in parallel — using fake
 * {@link MarketplaceClient} implementations rather than real marketplace calls.
 */
class CompareOffersServiceTest {

    private final SearchHistoryRecorder searchHistoryRecorder = Mockito.mock(SearchHistoryRecorder.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    /**
     * Verifies that the cheapest offer across all marketplaces is selected as the best offer, and that
     * the result is recorded.
     */
    @Test
    void picksCheapestOfferAmongAllMarketplaces() {
        MarketplaceClient ozon = query -> List.of(
                new MarketplaceOffer(Marketplace.OZON, "iPhone 15", new BigDecimal("74990"), "https://ozon.ru"));
        MarketplaceClient wildberries = query -> List.of(
                new MarketplaceOffer(Marketplace.WILDBERRIES, "iPhone 15", new BigDecimal("72990"), "https://wildberries.ru"));
        MarketplaceClient yandexMarket = query -> List.of(
                new MarketplaceOffer(Marketplace.YANDEX_MARKET, "iPhone 15", new BigDecimal("76990"), "https://market.yandex.ru"));

        CompareOffersService service = new CompareOffersService(List.of(ozon, wildberries, yandexMarket), searchHistoryRecorder, executor);

        ComparisonResult result = service.compare(new SearchQuery("iphone 15"));

        assertThat(result.totalOffers()).isEqualTo(3);
        assertThat(result.bestOffer()).isPresent();
        assertThat(result.bestOffer().get().marketplace()).isEqualTo(Marketplace.WILDBERRIES);
        assertThat(result.bestOffer().get().price()).isEqualByComparingTo("72990");
        verify(searchHistoryRecorder, times(1)).record(result);
    }

    /**
     * Verifies that a comparison with no offers from any marketplace yields an empty, but valid, result.
     */
    @Test
    void returnsEmptyResultWhenNoMarketplaceHasOffers() {
        MarketplaceClient emptyClient = query -> List.of();

        CompareOffersService service = new CompareOffersService(List.of(emptyClient), searchHistoryRecorder, executor);

        ComparisonResult result = service.compare(new SearchQuery("unknown product"));

        assertThat(result.offers()).isEmpty();
        assertThat(result.bestOffer()).isEmpty();
        assertThat(result.totalOffers()).isZero();
    }

    /**
     * Verifies that a blank query is rejected before any marketplace client is queried.
     */
    @Test
    void rejectsBlankQuery() {
        CompareOffersService service = new CompareOffersService(List.of(), searchHistoryRecorder, executor);

        assertThatThrownBy(() -> service.compare(new SearchQuery("  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies that one marketplace client throwing an exception doesn't prevent offers from the other,
     * still-working clients being returned.
     */
    @Test
    void skipsFailingMarketplaceClientAndKeepsOthers() {
        MarketplaceClient failing = query -> {
            throw new RuntimeException("marketplace unavailable");
        };
        MarketplaceClient ozon = query -> List.of(
                new MarketplaceOffer(Marketplace.OZON, "iPhone 15", new BigDecimal("74990"), "https://ozon.ru"));

        CompareOffersService service = new CompareOffersService(List.of(failing, ozon), searchHistoryRecorder, executor);

        ComparisonResult result = service.compare(new SearchQuery("iphone 15"));

        assertThat(result.totalOffers()).isEqualTo(1);
        assertThat(result.offers().get(0).marketplace()).isEqualTo(Marketplace.OZON);
    }

    /**
     * Verifies that marketplace clients are queried concurrently rather than one after another: two
     * clients that each take 300ms complete in well under the 600ms a sequential run would take.
     */
    @Test
    void queriesMarketplaceClientsInParallel() {
        Duration delay = Duration.ofMillis(300);
        MarketplaceClient slowOzon = query -> {
            sleep(delay);
            return List.of(new MarketplaceOffer(Marketplace.OZON, "iPhone 15", new BigDecimal("74990"), "https://ozon.ru"));
        };
        MarketplaceClient slowWildberries = query -> {
            sleep(delay);
            return List.of(new MarketplaceOffer(Marketplace.WILDBERRIES, "iPhone 15", new BigDecimal("72990"), "https://wildberries.ru"));
        };

        CompareOffersService service = new CompareOffersService(List.of(slowOzon, slowWildberries), searchHistoryRecorder, executor);

        Instant start = Instant.now();
        ComparisonResult result = service.compare(new SearchQuery("iphone 15"));
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(result.totalOffers()).isEqualTo(2);
        assertThat(elapsed).isLessThan(delay.multipliedBy(2));
    }

    private static void sleep(Duration duration) {
        try {
            TimeUnit.MILLISECONDS.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
