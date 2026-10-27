package com.memoriesmadevisible.publish;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/publish")
public class AdminPublishController {

    private final PublishService publish;

    public AdminPublishController(PublishService publish) {
        this.publish = publish;
    }

    /** Which kinds of content have unpublished changes. */
    @GetMapping
    public PublishService.Status status() {
        return publish.status();
    }

    @PostMapping
    public PublishService.Status publish() {
        return publish.publish();
    }

    @PostMapping("/discard")
    public PublishService.Status discard() {
        return publish.discard();
    }
}
