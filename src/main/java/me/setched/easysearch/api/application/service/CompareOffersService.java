package me.setched.easysearch.api.application.service;

import me.setched.easysearch.api.application.usecase.CompareOffersUseCase;
import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.domain.port.MarketplaceClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class CompareOffersService implements CompareOffersUseCase {

    private final List<MarketplaceClient> marketplaceClients;

    public CompareOffersService(List<MarketplaceClient> marketplaceClients) {
        this.marketplaceClients = marketplaceClients;
    }

    @Override
    public ComparisonResult compare(SearchQuery query) {
        if (query == null || query.query() == null || query.query().isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        List<MarketplaceOffer> offers = marketplaceClients.stream()
                .flatMap(client -> client.search(query).stream())
                .filter(Objects::nonNull)
                .toList();

        return new ComparisonResult(query, offers);
    }
}
