package me.setched.easysearch.api.infrastructure.usecase;

import me.setched.easysearch.api.application.service.CompareOffersService;
import me.setched.easysearch.api.application.usecase.CompareOffersUseCase;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import me.setched.easysearch.api.domain.port.SearchHistoryRecorder;
import me.setched.easysearch.api.infrastructure.marketplace.MarketplaceClientsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Assembles the {@link CompareOffersUseCase} bean: builds {@link CompareOffersService} and wraps it in
 * {@link TimeoutEnforcingCompareOffersUseCase}. {@link CompareOffersService} is deliberately not a Spring
 * component itself, to avoid both the raw and decorated instance being registered as separate beans.
 */
@Configuration
public class CompareOffersUseCaseConfig {

    /**
     * Builds the timeout-enforced use case bean.
     *
     * @param marketplaceClients        the marketplace clients to query
     * @param searchHistoryRecorder     where to record comparison outcomes
     * @param properties                shared marketplace resilience settings
     * @param marketplaceClientExecutor executor used both for parallel client queries and for enforcing the
     *                                  overall timeout
     * @return the timeout-enforced use case
     */
    @Bean
    public CompareOffersUseCase compareOffersUseCase(List<MarketplaceClient> marketplaceClients, SearchHistoryRecorder searchHistoryRecorder,
                                                       MarketplaceClientsProperties properties, ExecutorService marketplaceClientExecutor) {
        CompareOffersService compareOffersService = new CompareOffersService(marketplaceClients, searchHistoryRecorder, marketplaceClientExecutor);
        return new TimeoutEnforcingCompareOffersUseCase(compareOffersService, properties.compareTimeout(), marketplaceClientExecutor);
    }
}
