package com.memoriesmadevisible.photo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/photos")
public class AdminPhotoController {

    private final PhotoService photos;

    public AdminPhotoController(PhotoService photos) {
        this.photos = photos;
    }

    @GetMapping
    public List<PhotoService.GroupView> groups() {
        return photos.groupsForAdmin();
    }

    @PostMapping(path = "/{slotId}", consumes = "multipart/form-data")
    public PhotoService.PhotoView upload(@PathVariable String slotId, @RequestPart("file") MultipartFile file) {
        return photos.upload(slotId, file);
    }

    @DeleteMapping("/{slotId}")
    public ResponseEntity<Void> clear(@PathVariable String slotId) {
        photos.clear(slotId);
        return ResponseEntity.noContent().build();
    }
}
