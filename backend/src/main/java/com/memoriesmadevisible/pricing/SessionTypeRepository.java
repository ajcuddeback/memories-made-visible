package com.memoriesmadevisible.pricing;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionTypeRepository extends JpaRepository<SessionType, String> {

    @EntityGraph(attributePaths = "tiers")
    List<SessionType> findAllByOrderBySortOrderAsc();

    @EntityGraph(attributePaths = "tiers")
    Optional<SessionType> findWithTiersByKey(String key);
}
