package me.setched.easysearch.api.infrastructure.marketplace.ozon;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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
