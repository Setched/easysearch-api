package me.setched.easysearch.api.infrastructure.marketplace.ozon;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for connecting to the {@code ozon-scraper} service, bound from
 * {@code easysearch.marketplaces.ozon.*}.
 *
 * @param baseUrl        the ozon-scraper service base URL
 * @param connectTimeout HTTP connect timeout; defaults to 2 seconds if unset
 * @param readTimeout    HTTP read timeout; defaults to 5 seconds if unset
 */
@ConfigurationProperties(prefix = "easysearch.marketplaces.ozon")
public record OzonProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public OzonProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
