package me.setched.easysearch.api.infrastructure.marketplace.yandexmarket;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for connecting to the {@code yandexmarket-scraper} service, bound from
 * {@code easysearch.marketplaces.yandexmarket.*}.
 *
 * @param baseUrl        the yandexmarket-scraper service base URL
 * @param connectTimeout HTTP connect timeout; defaults to 2 seconds if unset
 * @param readTimeout    HTTP read timeout; defaults to 10 seconds if unset — unlike ozon-scraper and
 *                       wildberries-scraper, yandexmarket-scraper has no browser session to warm up, so it
 *                       doesn't need nearly as much headroom
 */
@ConfigurationProperties(prefix = "easysearch.marketplaces.yandexmarket")
public record YandexMarketProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public YandexMarketProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }
}
