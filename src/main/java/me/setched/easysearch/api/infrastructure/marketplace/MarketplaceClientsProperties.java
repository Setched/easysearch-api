package me.setched.easysearch.api.infrastructure.marketplace;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Resilience settings shared across all marketplace clients, bound from {@code easysearch.marketplaces.*}.
 *
 * @param searchTimeout  maximum time a single marketplace client is allowed to take; defaults to 3 seconds
 * @param compareTimeout maximum time the whole comparison (all clients combined) is allowed to take;
 *                       defaults to 5 seconds
 */
@ConfigurationProperties(prefix = "easysearch.marketplaces")
public record MarketplaceClientsProperties(Duration searchTimeout, Duration compareTimeout) {

    public MarketplaceClientsProperties {
        if (searchTimeout == null) {
            searchTimeout = Duration.ofSeconds(3);
        }
        if (compareTimeout == null) {
            compareTimeout = Duration.ofSeconds(5);
        }
    }
}
