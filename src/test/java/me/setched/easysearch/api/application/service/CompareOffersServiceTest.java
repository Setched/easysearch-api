package me.setched.easysearch.api.application.service;

import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import me.setched.easysearch.api.domain.port.SearchHistoryRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CompareOffersServiceTest {

    private final SearchHistoryRecorder searchHistoryRecorder = Mockito.mock(SearchHistoryRecorder.class);

    @Test
    void picksCheapestOfferAmongAllMarketplaces() {
        MarketplaceClient ozon = query -> List.of(
                new MarketplaceOffer(Marketplace.OZON, "iPhone 15", new BigDecimal("74990"), "https://ozon.ru"));
        MarketplaceClient wildberries = query -> List.of(
                new MarketplaceOffer(Marketplace.WILDBERRIES, "iPhone 15", new BigDecimal("72990"), "https://wildberries.ru"));
        MarketplaceClient yandexMarket = query -> List.of(
                new MarketplaceOffer(Marketplace.YANDEX_MARKET, "iPhone 15", new BigDecimal("76990"), "https://market.yandex.ru"));

        CompareOffersService service = new CompareOffersService(List.of(ozon, wildberries, yandexMarket), searchHistoryRecorder);

        ComparisonResult result = service.compare(new SearchQuery("iphone 15"));

        assertThat(result.totalOffers()).isEqualTo(3);
        assertThat(result.bestOffer()).isPresent();
        assertThat(result.bestOffer().get().marketplace()).isEqualTo(Marketplace.WILDBERRIES);
        assertThat(result.bestOffer().get().price()).isEqualByComparingTo("72990");
        verify(searchHistoryRecorder, times(1)).record(result);
    }

    @Test
    void returnsEmptyResultWhenNoMarketplaceHasOffers() {
        MarketplaceClient emptyClient = query -> List.of();

        CompareOffersService service = new CompareOffersService(List.of(emptyClient), searchHistoryRecorder);

        ComparisonResult result = service.compare(new SearchQuery("unknown product"));

        assertThat(result.offers()).isEmpty();
        assertThat(result.bestOffer()).isEmpty();
        assertThat(result.totalOffers()).isZero();
    }

    @Test
    void rejectsBlankQuery() {
        CompareOffersService service = new CompareOffersService(List.of(), searchHistoryRecorder);

        assertThatThrownBy(() -> service.compare(new SearchQuery("  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
