package me.setched.easysearch.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity persisting a single past search comparison: the query, how many offers were found, and the
 * cheapest one (if any).
 */
@Entity
@Table(name = "search_history")
public class SearchHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String query;

    @Column(name = "total_offers", nullable = false)
    private int totalOffers;

    @Column(name = "best_marketplace")
    private String bestMarketplace;

    @Column(name = "best_price")
    private BigDecimal bestPrice;

    @Column(name = "searched_at", nullable = false)
    private Instant searchedAt;

    /**
     * No-args constructor required by JPA.
     */
    protected SearchHistoryEntity() {
    }

    /**
     * Creates a new search history record.
     *
     * @param query           the search query text
     * @param totalOffers     the total number of offers found
     * @param bestMarketplace the name of the marketplace with the cheapest offer, or {@code null} if none
     * @param bestPrice       the cheapest offer's price, or {@code null} if none
     * @param searchedAt      when the search was performed
     */
    public SearchHistoryEntity(String query, int totalOffers, String bestMarketplace, BigDecimal bestPrice, Instant searchedAt) {
        this.query = query;
        this.totalOffers = totalOffers;
        this.bestMarketplace = bestMarketplace;
        this.bestPrice = bestPrice;
        this.searchedAt = searchedAt;
    }

    /**
     * @return the generated primary key
     */
    public Long getId() {
        return id;
    }

    /**
     * @return the search query text
     */
    public String getQuery() {
        return query;
    }

    /**
     * @return the total number of offers found
     */
    public int getTotalOffers() {
        return totalOffers;
    }

    /**
     * @return the name of the marketplace with the cheapest offer, or {@code null} if none
     */
    public String getBestMarketplace() {
        return bestMarketplace;
    }

    /**
     * @return the cheapest offer's price, or {@code null} if none
     */
    public BigDecimal getBestPrice() {
        return bestPrice;
    }

    /**
     * @return when the search was performed
     */
    public Instant getSearchedAt() {
        return searchedAt;
    }
}
