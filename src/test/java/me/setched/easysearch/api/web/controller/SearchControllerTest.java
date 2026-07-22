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

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompareOffersUseCase compareOffersUseCase;

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
                .andExpect(jsonPath("$.offers.length()").value(2));
    }

    @Test
    void rejectsBlankQueryWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
