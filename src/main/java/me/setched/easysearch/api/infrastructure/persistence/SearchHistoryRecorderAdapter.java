package me.setched.easysearch.api.infrastructure.persistence;

import me.setched.easysearch.api.domain.model.ComparisonResult;
import me.setched.easysearch.api.domain.port.SearchHistoryRecorder;
import me.setched.easysearch.api.infrastructure.persistence.mapper.SearchHistoryMapper;
import me.setched.easysearch.api.infrastructure.persistence.repository.SearchHistoryJpaRepository;
import org.springframework.stereotype.Component;

/**
 * {@link SearchHistoryRecorder} adapter backed by JPA: maps a {@link ComparisonResult} to a
 * {@link me.setched.easysearch.api.infrastructure.persistence.entity.SearchHistoryEntity} and persists it.
 */
@Component
public class SearchHistoryRecorderAdapter implements SearchHistoryRecorder {

    private final SearchHistoryJpaRepository repository;
    private final SearchHistoryMapper mapper;

    /**
     * Creates an adapter backed by the given repository and mapper.
     *
     * @param repository the JPA repository to persist to
     * @param mapper     maps domain results to persistence entities
     */
    public SearchHistoryRecorderAdapter(SearchHistoryJpaRepository repository, SearchHistoryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(ComparisonResult result) {
        repository.save(mapper.toEntity(result));
    }
}
