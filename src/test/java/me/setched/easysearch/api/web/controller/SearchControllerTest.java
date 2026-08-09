package me.setched.easysearch.api.web.controller;

import me.setched.easysearch.api.application.usecase.CompareOffersUseCase;
import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@link SearchController}'s HTTP contract (status codes, JSON shape, sorting/pagination,
 * error handling) with {@link CompareOffersUseCase} mocked out.
 */
@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompareOffersUseCase compareOffersUseCase;

    /**
     * Verifies that a valid query returns a 200 response with the expected search response shape.
     */
    @Test
    void returnsSearchResultsForValidQuery() throws Exception {
        ComparisonResult result = new ComparisonResult(new SearchQuery("iphone 15"), List.of(
                new MarketplaceOffer(Marketplace.WILDBERRIES, "iPhone 15", new BigDecimal("72990"), "https://wildberries.ru"),
                new MarketplaceOffer(Marketplace.OZON, "iPhone 15", new BigDecimal("74990"), "https://ozon.ru")));
        when(compareOffersUseCase.compare(any())).thenReturn(result);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"iphone 15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("iphone 15"))
                .andExpect(jsonPath("$.totalOffers").value(2))
                .andExpect(jsonPath("$.bestOffer.marketplace").value("WILDBERRIES"))
                .andExpect(jsonPath("$.offers.length()").value(2))
                .andExpect(jsonPath("$.offers[0].marketplace").value("WILDBERRIES"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    /**
     * Verifies that requesting {@code PRICE_DESC} sort returns offers from most to least expensive.
     */
    @Test
    void sortsOffersByPriceDescendingWhenRequested() throws Exception {
        ComparisonResult result = new ComparisonResult(new SearchQuery("iphone 15"), List.of(
                new MarketplaceOffer(Marketplace.WILDBERRIES, "iPhone 15", new BigDecimal("72990"), "https://wildberries.ru"),
                new MarketplaceOffer(Marketplace.OZON, "iPhone 15", new BigDecimal("74990"), "https://ozon.ru")));
        when(compareOffersUseCase.compare(any())).thenReturn(result);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"iphone 15\",\"sort\":\"PRICE_DESC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].marketplace").value("OZON"))
                .andExpect(jsonPath("$.offers[1].marketplace").value("WILDBERRIES"));
    }

    /**
     * Verifies that page/size parameters correctly slice the offer list and compute total pages.
     */
    @Test
    void paginatesOffersAccordingToPageAndSize() throws Exception {
        ComparisonResult result = new ComparisonResult(new SearchQuery("iphone 15"), List.of(
                new MarketplaceOffer(Marketplace.WILDBERRIES, "iPhone 15", new BigDecimal("72990"), "https://wildberries.ru"),
                new MarketplaceOffer(Marketplace.OZON, "iPhone 15", new BigDecimal("74990"), "https://ozon.ru"),
                new MarketplaceOffer(Marketplace.YANDEX_MARKET, "iPhone 15", new BigDecimal("76990"), "https://market.yandex.ru")));
        when(compareOffersUseCase.compare(any())).thenReturn(result);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"iphone 15\",\"page\":1,\"size\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOffers").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.offers.length()").value(1))
                .andExpect(jsonPath("$.offers[0].marketplace").value("YANDEX_MARKET"));
    }

    /**
     * Verifies that a blank query is rejected with a 400 response listing the validation failure.
     */
    @Test
    void rejectsBlankQueryWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details[0]").value("query: must not be blank"));
    }

    /**
     * Verifies that an {@link IllegalArgumentException} from the use case is translated into a 400
     * response carrying its message.
     */
    @Test
    void returnsBadRequestWhenUseCaseRejectsQuery() throws Exception {
        when(compareOffersUseCase.compare(any())).thenThrow(new IllegalArgumentException("Search query must not be blank"));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"whatever\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Search query must not be blank"));
    }

    /**
     * Verifies that an unexpected exception from the use case is translated into a generic 500 response,
     * without leaking the exception's details.
     */
    @Test
    void returnsInternalServerErrorOnUnexpectedFailure() throws Exception {
        when(compareOffersUseCase.compare(any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"iphone 15\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Something went wrong"));
    }
}
