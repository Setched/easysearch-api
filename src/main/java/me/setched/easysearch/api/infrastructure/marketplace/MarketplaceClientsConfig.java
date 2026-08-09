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

/**
 * Assembles every {@link MarketplaceClient} bean, wrapping each raw client in
 * {@link TimeoutEnforcingMarketplaceClient} so timeout handling is applied uniformly regardless of how the
 * underlying integration works. Raw clients are deliberately not Spring components themselves, to avoid
 * both the raw and decorated instance being registered as separate beans.
 */
@Configuration
public class MarketplaceClientsConfig {

    /**
     * Shared executor used to run marketplace searches and enforce timeouts on them.
     *
     * @return a virtual-thread-per-task executor
     */
    @Bean
    public ExecutorService marketplaceClientExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * The Ozon client, wrapped with a timeout.
     *
     * @param ozonRestClient          the configured REST client for Ozon
     * @param properties              shared marketplace resilience settings
     * @param marketplaceClientExecutor executor used to enforce the timeout
     * @return the timeout-enforced Ozon client
     */
    @Bean
    public MarketplaceClient ozonMarketplaceClient(RestClient ozonRestClient, MarketplaceClientsProperties properties,
                                                     ExecutorService marketplaceClientExecutor) {
        return new TimeoutEnforcingMarketplaceClient(
                "Ozon", new OzonMarketplaceClient(ozonRestClient), properties.searchTimeout(), marketplaceClientExecutor);
    }

    /**
     * The Wildberries client, wrapped with a timeout.
     *
     * @param properties              shared marketplace resilience settings
     * @param marketplaceClientExecutor executor used to enforce the timeout
     * @return the timeout-enforced Wildberries client
     */
    @Bean
    public MarketplaceClient wildberriesMarketplaceClient(MarketplaceClientsProperties properties, ExecutorService marketplaceClientExecutor) {
        return new TimeoutEnforcingMarketplaceClient(
                "Wildberries", new WildberriesMarketplaceClient(), properties.searchTimeout(), marketplaceClientExecutor);
    }

    /**
     * The Yandex Market client, wrapped with a timeout.
     *
     * @param properties              shared marketplace resilience settings
     * @param marketplaceClientExecutor executor used to enforce the timeout
     * @return the timeout-enforced Yandex Market client
     */
    @Bean
    public MarketplaceClient yandexMarketMarketplaceClient(MarketplaceClientsProperties properties, ExecutorService marketplaceClientExecutor) {
        return new TimeoutEnforcingMarketplaceClient(
                "Yandex Market", new YandexMarketMarketplaceClient(), properties.searchTimeout(), marketplaceClientExecutor);
    }
}
