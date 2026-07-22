package me.setched.easysearch.api.application.service;

import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompareOffersServiceTest {

    @Test
    void picksCheapestOfferAmongAllMarketplaces() {
        MarketplaceClient ozon = query -> List.of(
                new MarketplaceOffer(Marketplace.OZON, "iPhone 15", new BigDecimal("74990"), "https://ozon.ru"));
        MarketplaceClient wildberries = query -> List.of(
                new MarketplaceOffer(Marketplace.WILDBERRIES, "iPhone 15", new BigDecimal("72990"), "https://wildberries.ru"));
        MarketplaceClient yandexMarket = query -> List.of(
                new MarketplaceOffer(Marketplace.YANDEX_MARKET, "iPhone 15", new BigDecimal("76990"), "https://market.yandex.ru"));

        CompareOffersService service = new CompareOffersService(List.of(ozon, wildberries, yandexMarket));

        ComparisonResult result = service.compare(new SearchQuery("iphone 15"));

        assertThat(result.totalOffers()).isEqualTo(3);
        assertThat(result.bestOffer()).isPresent();
        assertThat(result.bestOffer().get().marketplace()).isEqualTo(Marketplace.WILDBERRIES);
        assertThat(result.bestOffer().get().price()).isEqualByComparingTo("72990");
    }

    @Test
    void returnsEmptyResultWhenNoMarketplaceHasOffers() {
        MarketplaceClient emptyClient = query -> List.of();

        CompareOffersService service = new CompareOffersService(List.of(emptyClient));

        ComparisonResult result = service.compare(new SearchQuery("unknown product"));

        assertThat(result.offers()).isEmpty();
        assertThat(result.bestOffer()).isEmpty();
        assertThat(result.totalOffers()).isZero();
    }

    @Test
    void rejectsBlankQuery() {
        CompareOffersService service = new CompareOffersService(List.of());

        assertThatThrownBy(() -> service.compare(new SearchQuery("  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
