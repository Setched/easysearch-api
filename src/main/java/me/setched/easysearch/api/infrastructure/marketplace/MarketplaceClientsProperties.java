package me.setched.easysearch.api.infrastructure.marketplace;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "easysearch.marketplaces")
public record MarketplaceClientsProperties(Duration searchTimeout) {

    public MarketplaceClientsProperties {
        if (searchTimeout == null) {
            searchTimeout = Duration.ofSeconds(3);
        }
    }
}
