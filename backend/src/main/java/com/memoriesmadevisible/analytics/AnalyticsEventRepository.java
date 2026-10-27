package com.memoriesmadevisible.analytics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {
    List<AnalyticsEvent> findAllByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(Instant from);
}
