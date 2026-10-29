package com.memoriesmadevisible.photo;

import com.memoriesmadevisible.publish.PublishService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public: slot → image URL map consumed by every page that shows a photo. */
@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PublishService publish;

    public PhotoController(PublishService publish) {
        this.publish = publish;
    }

    /** Published slot → URL map. */
    @GetMapping
    public Map<String, PhotoDtos.PhotoUrls> all() {
        return publish.publishedPhotoUrls();
    }
}
