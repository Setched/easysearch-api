package me.setched.easysearch.api.infrastructure.persistence.repository;

import me.setched.easysearch.api.infrastructure.persistence.entity.SearchHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link SearchHistoryEntity}.
 */
public interface SearchHistoryJpaRepository extends JpaRepository<SearchHistoryEntity, Long> {
}
