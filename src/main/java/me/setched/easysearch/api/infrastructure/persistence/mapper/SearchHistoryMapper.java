package me.setched.easysearch.api.infrastructure.persistence.mapper;

import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.model.MarketplaceOffer;
import me.setched.easysearch.api.infrastructure.persistence.entity.SearchHistoryEntity;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Maps a domain {@link ComparisonResult} to a {@link SearchHistoryEntity} for persistence.
 */
@Component
public class SearchHistoryMapper {

    private final Clock clock;

    /**
     * Creates a mapper that timestamps entities using the given clock.
     *
     * @param clock the clock used to record when the mapping happened
     */
    public SearchHistoryMapper(Clock clock) {
        this.clock = clock;
    }

    /**
     * Converts a comparison result into a persistable entity.
     *
     * @param result the comparison result to convert
     * @return the corresponding entity, timestamped with the current time
     */
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
