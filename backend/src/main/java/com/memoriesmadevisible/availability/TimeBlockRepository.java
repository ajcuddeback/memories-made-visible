package com.memoriesmadevisible.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TimeBlockRepository extends JpaRepository<TimeBlock, Long> {
    /** Blocks touching [from, to]. */
    List<TimeBlock> findAllByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscStartTimeAsc(LocalDate from, LocalDate to);
}
