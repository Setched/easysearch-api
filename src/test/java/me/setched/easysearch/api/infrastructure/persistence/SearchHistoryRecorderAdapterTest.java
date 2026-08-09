package me.setched.easysearch.api.infrastructure.persistence;

import me.setched.easysearch.api.TestcontainersConfiguration;
import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.Marketplace;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.domain.model.SearchQuery;
import me.setched.easysearch.api.infrastructure.persistence.entity.SearchHistoryEntity;
import me.setched.easysearch.api.infrastructure.persistence.mapper.SearchHistoryMapper;
import me.setched.easysearch.api.infrastructure.persistence.repository.SearchHistoryJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Verifies {@link SearchHistoryRecorderAdapter} against a real Postgres instance, provided by
 * {@link TestcontainersConfiguration}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
@EntityScan(basePackageClasses = SearchHistoryEntity.class)
@EnableJpaRepositories(basePackageClasses = SearchHistoryJpaRepository.class)
class SearchHistoryRecorderAdapterTest {

    @Autowired
    private SearchHistoryJpaRepository repository;

    /**
     * Verifies that recording a comparison result persists a matching {@link SearchHistoryEntity} row.
     */
    @Test
    void persistsSearchHistoryForComparisonResult() {
        SearchHistoryMapper mapper = new SearchHistoryMapper(Clock.systemUTC());
        SearchHistoryRecorderAdapter adapter = new SearchHistoryRecorderAdapter(repository, mapper);

        ComparisonResult result = new ComparisonResult(new SearchQuery("iphone 15"), List.of(
                new MarketplaceOffer(Marketplace.WILDBERRIES, "iPhone 15", new BigDecimal("72990"), "https://wildberries.ru")));

        adapter.record(result);

        List<SearchHistoryEntity> saved = repository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getQuery()).isEqualTo("iphone 15");
        assertThat(saved.get(0).getTotalOffers()).isEqualTo(1);
        assertThat(saved.get(0).getBestMarketplace()).isEqualTo("WILDBERRIES");
        assertThat(saved.get(0).getBestPrice()).isEqualByComparingTo("72990");
    }
}
