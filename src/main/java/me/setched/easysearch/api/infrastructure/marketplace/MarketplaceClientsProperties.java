package me.setched.easysearch.api.infrastructure.marketplace;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Resilience settings shared across all marketplace clients, bound from {@code easysearch.marketplaces.*}.
 * Defaults are sized for the slowest current client (Ozon's scraper, which can take 10+ seconds on a
 * cold session) rather than the fastest — clients run in parallel, so this doesn't slow down fast ones.
 *
 * @param searchTimeout  maximum time a single marketplace client is allowed to take; defaults to 20 seconds
 * @param compareTimeout maximum time the whole comparison (all clients combined) is allowed to take;
 *                       defaults to 25 seconds
 */
@ConfigurationProperties(prefix = "easysearch.marketplaces")
public record MarketplaceClientsProperties(Duration searchTimeout, Duration compareTimeout) {

    public MarketplaceClientsProperties {
        if (searchTimeout == null) {
            searchTimeout = Duration.ofSeconds(20);
        }
        if (compareTimeout == null) {
            compareTimeout = Duration.ofSeconds(25);
        }
    }
}
