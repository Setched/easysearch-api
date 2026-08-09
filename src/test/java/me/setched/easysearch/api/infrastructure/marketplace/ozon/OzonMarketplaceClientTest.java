package me.setched.easysearch.api.infrastructure.marketplace.ozon;

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
 * Verifies {@link OzonMarketplaceClient}'s HTTP request/response handling against a mocked REST server.
 * Does not exercise the real Ozon API.
 */
class OzonMarketplaceClientTest {

    /**
     * Verifies that a successful Ozon response is correctly mapped into {@link MarketplaceOffer} instances.
     */
    @Test
    void mapsOzonSearchResponseToMarketplaceOffers() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("https://api-seller.ozon.ru");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();

        server.expect(requestTo("https://api-seller.ozon.ru/v1/search?text=iphone%2015"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {"name": "Apple iPhone 15 128GB", "price": 74990, "url": "https://www.ozon.ru/product/iphone-15"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        OzonMarketplaceClient client = new OzonMarketplaceClient(restClientBuilder.build());

        List<MarketplaceOffer> offers = client.search(new SearchQuery("iphone 15"));

        assertThat(offers).hasSize(1);
        MarketplaceOffer offer = offers.get(0);
        assertThat(offer.marketplace()).isEqualTo(Marketplace.OZON);
        assertThat(offer.title()).isEqualTo("Apple iPhone 15 128GB");
        assertThat(offer.price()).isEqualByComparingTo("74990");
        assertThat(offer.url()).isEqualTo("https://www.ozon.ru/product/iphone-15");

        server.verify();
    }

    /**
     * Verifies that a response with no items yields an empty offer list rather than an error.
     */
    @Test
    void returnsEmptyListWhenResponseHasNoItems() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("https://api-seller.ozon.ru");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();

        server.expect(requestTo("https://api-seller.ozon.ru/v1/search?text=unknown"))
                .andRespond(withSuccess("{\"items\": []}", MediaType.APPLICATION_JSON));

        OzonMarketplaceClient client = new OzonMarketplaceClient(restClientBuilder.build());

        List<MarketplaceOffer> offers = client.search(new SearchQuery("unknown"));

        assertThat(offers).isEmpty();
    }
}
