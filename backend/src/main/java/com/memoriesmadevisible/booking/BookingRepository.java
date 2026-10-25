package com.memoriesmadevisible.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findAllByOrderByDateAscTimeAsc();
    long countByStatus(BookingStatus status);
}
