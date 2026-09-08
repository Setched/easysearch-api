package me.setched.easysearch.api.infrastructure.marketplace.wildberries;

import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies {@link WildberriesMarketplaceClient}'s HTTP request/response handling against a mocked
 * REST server. Does not exercise the real wildberries-scraper service or the live Wildberries site.
 */
class WildberriesMarketplaceClientTest {

    /**
     * Verifies that a successful Wildberries response is correctly mapped into
     * {@link MarketplaceOffer} instances.
     */
    @Test
    void mapsWildberriesSearchResponseToMarketplaceOffers() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("http://localhost:8001");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();

        server.expect(requestTo("http://localhost:8001/search?query=iphone%2015"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {"name": "Apple iPhone 15 128GB", "price": 72990, "url": "https://www.wildberries.ru/catalog/123456/detail.aspx"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        WildberriesMarketplaceClient client = new WildberriesMarketplaceClient(restClientBuilder.build());

        List<MarketplaceOffer> offers = client.search(new SearchQuery("iphone 15"));

        assertThat(offers).hasSize(1);
        MarketplaceOffer offer = offers.get(0);
        assertThat(offer.marketplace()).isEqualTo(Marketplace.WILDBERRIES);
        assertThat(offer.title()).isEqualTo("Apple iPhone 15 128GB");
        assertThat(offer.price()).isEqualByComparingTo("72990");
        assertThat(offer.url()).isEqualTo("https://www.wildberries.ru/catalog/123456/detail.aspx");

        server.verify();
    }

    /**
     * Verifies that a response with no items yields an empty offer list rather than an error.
     */
    @Test
    void returnsEmptyListWhenResponseHasNoItems() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("http://localhost:8001");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();

        server.expect(requestTo("http://localhost:8001/search?query=unknown"))
                .andRespond(withSuccess("{\"items\": []}", MediaType.APPLICATION_JSON));

        WildberriesMarketplaceClient client = new WildberriesMarketplaceClient(restClientBuilder.build());

        List<MarketplaceOffer> offers = client.search(new SearchQuery("unknown"));

        assertThat(offers).isEmpty();
    }
}
