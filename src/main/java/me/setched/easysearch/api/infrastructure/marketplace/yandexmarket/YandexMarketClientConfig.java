package me.setched.easysearch.api.infrastructure.marketplace.yandexmarket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Wires up the {@link RestClient} used by {@link YandexMarketMarketplaceClient} to call the
 * {@code yandexmarket-scraper} service, applying connection/read timeouts from
 * {@link YandexMarketProperties}.
 */
@Configuration
public class YandexMarketClientConfig {

    /**
     * Builds the REST client pointed at the yandexmarket-scraper service.
     *
     * @param properties yandexmarket-scraper connection settings
     * @return a configured {@link RestClient} for the yandexmarket-scraper service
     */
    @Bean
    public RestClient yandexMarketRestClient(YandexMarketProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
