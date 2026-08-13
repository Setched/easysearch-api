package me.setched.easysearch.api.infrastructure.marketplace.ozon;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Wires up the {@link RestClient} used by {@link OzonMarketplaceClient} to call the {@code ozon-scraper}
 * service, applying connection/read timeouts from {@link OzonProperties}.
 */
@Configuration
public class OzonClientConfig {

    /**
     * Builds the REST client pointed at the ozon-scraper service.
     *
     * @param properties ozon-scraper connection settings
     * @return a configured {@link RestClient} for the ozon-scraper service
     */
    @Bean
    public RestClient ozonRestClient(OzonProperties properties) {
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
