package com.memoriesmadevisible.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PopupEventRepository extends JpaRepository<PopupEvent, Long> {
    List<PopupEvent> findAllByOrderByDateAsc();
    List<PopupEvent> findAllByDateGreaterThanEqualOrderByDateAsc(LocalDate from);
}
