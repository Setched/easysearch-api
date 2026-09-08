package me.setched.easysearch.api.infrastructure.marketplace.wildberries;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for connecting to the {@code wildberries-scraper} service, bound from
 * {@code easysearch.marketplaces.wildberries.*}.
 *
 * @param baseUrl        the wildberries-scraper service base URL
 * @param connectTimeout HTTP connect timeout; defaults to 2 seconds if unset
 * @param readTimeout    HTTP read timeout; defaults to 20 seconds if unset — like ozon-scraper,
 *                       wildberries-scraper can take several seconds on a cold browser session
 */
@ConfigurationProperties(prefix = "easysearch.marketplaces.wildberries")
public record WildberriesProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public WildberriesProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(20);
        }
    }
}
