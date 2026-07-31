package me.setched.easysearch.api.domain.port;

import me.setched.easysearch.api.domain.model.ComparisonResult;

public interface SearchHistoryRecorder {

    void record(ComparisonResult result);
}
