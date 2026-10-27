package com.memoriesmadevisible.event;

import com.memoriesmadevisible.publish.PublishService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public: "Upcoming pop-ups" on the Events page. */
@RestController
@RequestMapping("/api/events")
public class PopupEventController {

    private final PublishService publish;

    public PopupEventController(PublishService publish) {
        this.publish = publish;
    }

    /** Published pop-ups from today on. */
    @GetMapping("/upcoming")
    public List<PopupEventDtos.PublicResponse> upcoming() {
        return publish.publishedUpcoming();
    }
}
