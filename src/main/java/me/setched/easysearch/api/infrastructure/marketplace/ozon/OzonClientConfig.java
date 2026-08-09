package me.setched.easysearch.api.infrastructure.marketplace.ozon;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Wires up the {@link RestClient} used by {@link OzonMarketplaceClient}, applying connection/read timeouts
 * and authentication headers from {@link OzonProperties}.
 */
@Configuration
public class OzonClientConfig {

    /**
     * Builds the Ozon-specific REST client.
     *
     * @param properties Ozon connection settings
     * @return a configured {@link RestClient} for Ozon's Seller API
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
                .defaultHeader("Client-Id", properties.clientId())
                .defaultHeader("Api-Key", properties.apiKey())
                .build();
    }
}
