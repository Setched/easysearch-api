package me.setched.easysearch.api.infrastructure.persistence.mapper;

import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.infrastructure.persistence.entity.SearchHistoryEntity;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class SearchHistoryMapper {

    private final Clock clock;

    public SearchHistoryMapper(Clock clock) {
        this.clock = clock;
    }

    public SearchHistoryEntity toEntity(ComparisonResult result) {
        MarketplaceOffer bestOffer = result.bestOffer().orElse(null);

        return new SearchHistoryEntity(
                result.query().query(),
                result.totalOffers(),
                bestOffer != null ? bestOffer.marketplace().name() : null,
                bestOffer != null ? bestOffer.price() : null,
                Instant.now(clock));
    }
}
