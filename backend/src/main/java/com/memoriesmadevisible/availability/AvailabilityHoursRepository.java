package com.memoriesmadevisible.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvailabilityHoursRepository extends JpaRepository<AvailabilityHours, Integer> {
    List<AvailabilityHours> findAllByOrderByWeekdayAsc();
}
