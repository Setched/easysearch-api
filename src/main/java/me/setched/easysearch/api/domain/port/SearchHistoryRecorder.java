package me.setched.easysearch.api.domain.port;

import me.setched.easysearch.api.domain.model.ComparisonResult;

/**
 * A port describing the domain's need to persist the outcome of a comparison for later reference.
 * Implementations (adapters) in the infrastructure layer decide how and where it's stored.
 */
public interface SearchHistoryRecorder {

    /**
     * Records the outcome of a comparison.
     *
     * @param result the comparison result to record
     */
    void record(ComparisonResult result);
}
