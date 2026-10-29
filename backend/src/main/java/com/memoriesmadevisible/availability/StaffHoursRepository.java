package com.memoriesmadevisible.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffHoursRepository extends JpaRepository<StaffHours, StaffHours.Key> {
    List<StaffHours> findAllByIdStaffNameIgnoreCaseOrderByIdWeekdayAsc(String staffName);
    void deleteAllByIdStaffNameIgnoreCase(String staffName);
}
