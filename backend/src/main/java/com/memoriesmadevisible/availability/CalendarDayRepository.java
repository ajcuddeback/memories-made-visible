package com.memoriesmadevisible.availability;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface CalendarDayRepository extends JpaRepository<CalendarDay, LocalDate> {

    /** Make sure the day's lock row exists (a lost race here is fine — the row is there either way). */
    @Modifying
    @Query(value = "INSERT INTO calendar_day (cal_date) VALUES (:day) ON CONFLICT DO NOTHING", nativeQuery = true)
    void ensure(@Param("day") LocalDate day);

    /**
     * {@code SELECT … FOR UPDATE} on the day's row. Held until the transaction ends, so the
     * availability check and the insert that follows are atomic across concurrent requests.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CalendarDay d where d.day = :day")
    Optional<CalendarDay> lock(@Param("day") LocalDate day);
}
