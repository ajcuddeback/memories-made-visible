package com.memoriesmadevisible.event;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Public: "Upcoming pop-ups" on the Events page. */
@RestController
@RequestMapping("/api/events")
public class PopupEventController {

    private final PopupEventRepository events;

    public PopupEventController(PopupEventRepository events) {
        this.events = events;
    }

    @GetMapping("/upcoming")
    public List<PopupEventDtos.PublicResponse> upcoming() {
        return events.findAllByDateGreaterThanEqualOrderByDateAsc(LocalDate.now()).stream()
                .map(PopupEventDtos::toPublic).toList();
    }
}
