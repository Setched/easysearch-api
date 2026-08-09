package me.setched.easysearch.api.infrastructure.marketplace.ozon;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for connecting to Ozon's Seller API, bound from {@code easysearch.marketplaces.ozon.*}.
 *
 * @param baseUrl        the Ozon API base URL
 * @param apiKey         the seller API key
 * @param clientId       the seller client ID
 * @param connectTimeout HTTP connect timeout; defaults to 2 seconds if unset
 * @param readTimeout    HTTP read timeout; defaults to 5 seconds if unset
 */
@ConfigurationProperties(prefix = "easysearch.marketplaces.ozon")
public record OzonProperties(String baseUrl, String apiKey, String clientId, Duration connectTimeout, Duration readTimeout) {

    public OzonProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
