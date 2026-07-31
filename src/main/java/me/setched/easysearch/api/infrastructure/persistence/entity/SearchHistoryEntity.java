package me.setched.easysearch.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

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

    protected SearchHistoryEntity() {
    }

    public SearchHistoryEntity(String query, int totalOffers, String bestMarketplace, BigDecimal bestPrice, Instant searchedAt) {
        this.query = query;
        this.totalOffers = totalOffers;
        this.bestMarketplace = bestMarketplace;
        this.bestPrice = bestPrice;
        this.searchedAt = searchedAt;
    }

    public Long getId() {
        return id;
    }

    public String getQuery() {
        return query;
    }

    public int getTotalOffers() {
        return totalOffers;
    }

    public String getBestMarketplace() {
        return bestMarketplace;
    }

    public BigDecimal getBestPrice() {
        return bestPrice;
    }

    public Instant getSearchedAt() {
        return searchedAt;
    }
}
