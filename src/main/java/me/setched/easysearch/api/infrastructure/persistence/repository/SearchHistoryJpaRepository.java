package me.setched.easysearch.api.infrastructure.persistence.repository;

import me.setched.easysearch.api.infrastructure.persistence.entity.SearchHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchHistoryJpaRepository extends JpaRepository<SearchHistoryEntity, Long> {
}
