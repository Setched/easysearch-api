package me.setched.easysearch.api.infrastructure.marketplace.wildberries;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Wires up the {@link RestClient} used by {@link WildberriesMarketplaceClient} to call the
 * {@code wildberries-scraper} service, applying connection/read timeouts from
 * {@link WildberriesProperties}.
 */
@Configuration
public class WildberriesClientConfig {

    /**
     * Builds the REST client pointed at the wildberries-scraper service.
     *
     * @param properties wildberries-scraper connection settings
     * @return a configured {@link RestClient} for the wildberries-scraper service
     */
    @Bean
    public RestClient wildberriesRestClient(WildberriesProperties properties) {
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
