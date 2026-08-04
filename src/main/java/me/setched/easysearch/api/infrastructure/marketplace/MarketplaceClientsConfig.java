package me.setched.easysearch.api.infrastructure.marketplace;

import me.setched.easysearch.api.domain.port.MarketplaceClient;
import me.setched.easysearch.api.infrastructure.marketplace.ozon.OzonMarketplaceClient;
import me.setched.easysearch.api.infrastructure.marketplace.wildberries.WildberriesMarketplaceClient;
import me.setched.easysearch.api.infrastructure.marketplace.yandexmarket.YandexMarketMarketplaceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class MarketplaceClientsConfig {

    @Bean
    public ExecutorService marketplaceClientExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public MarketplaceClient ozonMarketplaceClient(RestClient ozonRestClient, MarketplaceClientsProperties properties,
                                                     ExecutorService marketplaceClientExecutor) {
        return new TimeoutEnforcingMarketplaceClient(
                "Ozon", new OzonMarketplaceClient(ozonRestClient), properties.searchTimeout(), marketplaceClientExecutor);
    }

    @Bean
    public MarketplaceClient wildberriesMarketplaceClient(MarketplaceClientsProperties properties, ExecutorService marketplaceClientExecutor) {
        return new TimeoutEnforcingMarketplaceClient(
                "Wildberries", new WildberriesMarketplaceClient(), properties.searchTimeout(), marketplaceClientExecutor);
    }

    @Bean
    public MarketplaceClient yandexMarketMarketplaceClient(MarketplaceClientsProperties properties, ExecutorService marketplaceClientExecutor) {
        return new TimeoutEnforcingMarketplaceClient(
                "Yandex Market", new YandexMarketMarketplaceClient(), properties.searchTimeout(), marketplaceClientExecutor);
    }
}
