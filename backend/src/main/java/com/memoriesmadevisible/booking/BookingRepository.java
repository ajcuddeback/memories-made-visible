package com.memoriesmadevisible.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findAllByOrderByDateAscTimeAsc();
    long countByStatus(BookingStatus status);
    long countByStatusAndDateGreaterThanEqual(BookingStatus status, LocalDate date);
    List<Booking> findAllByDateBetweenAndStatusNot(LocalDate from, LocalDate to, BookingStatus status);
}
