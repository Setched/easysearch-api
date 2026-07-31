package me.setched.easysearch.api.infrastructure.persistence;

import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.port.SearchHistoryRecorder;
import me.setched.easysearch.api.infrastructure.persistence.mapper.SearchHistoryMapper;
import me.setched.easysearch.api.infrastructure.persistence.repository.SearchHistoryJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class SearchHistoryRecorderAdapter implements SearchHistoryRecorder {

    private final SearchHistoryJpaRepository repository;
    private final SearchHistoryMapper mapper;

    public SearchHistoryRecorderAdapter(SearchHistoryJpaRepository repository, SearchHistoryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void record(ComparisonResult result) {
        repository.save(mapper.toEntity(result));
    }
}
