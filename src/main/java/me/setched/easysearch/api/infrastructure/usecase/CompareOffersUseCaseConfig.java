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

@Configuration
public class CompareOffersUseCaseConfig {

    @Bean
    public CompareOffersUseCase compareOffersUseCase(List<MarketplaceClient> marketplaceClients, SearchHistoryRecorder searchHistoryRecorder,
                                                       MarketplaceClientsProperties properties, ExecutorService marketplaceClientExecutor) {
        CompareOffersService compareOffersService = new CompareOffersService(marketplaceClients, searchHistoryRecorder, marketplaceClientExecutor);
        return new TimeoutEnforcingCompareOffersUseCase(compareOffersService, properties.compareTimeout(), marketplaceClientExecutor);
    }
}
