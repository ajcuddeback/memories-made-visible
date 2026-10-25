package com.memoriesmadevisible.photo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public: slot → image URL map consumed by every page that shows a photo. */
@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService photos;

    public PhotoController(PhotoService photos) {
        this.photos = photos;
    }

    @GetMapping
    public Map<String, String> all() {
        return photos.urlsBySlot();
    }
}
