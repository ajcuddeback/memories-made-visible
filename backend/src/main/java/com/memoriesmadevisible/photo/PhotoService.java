package com.memoriesmadevisible.photo;

import com.memoriesmadevisible.common.NotFoundException;
import com.memoriesmadevisible.pricing.PricingService;
import com.memoriesmadevisible.publish.PublishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/avif");

    private final SitePhotoRepository photos;
    private final PhotoStorage storage;
    private final ImageProcessor images;
    private final PricingService pricing;
    private final PublishService publish;

    public PhotoService(SitePhotoRepository photos, PhotoStorage storage, ImageProcessor images, PricingService pricing,
                        PublishService publish) {
        this.photos = photos;
        this.storage = storage;
        this.images = images;
        this.pricing = pricing;
        this.publish = publish;
    }

    public record PhotoView(String slotId, String url, String contentType) {}
    public record GroupView(String title, List<SlotView> slots) {}
    public record SlotView(String id, String label, String url) {}

    /** slotId → URL for every filled slot in the draft (the admin grid). */
    @Transactional(readOnly = true)
    public Map<String, String> urlsBySlot() {
        Map<String, String> out = new LinkedHashMap<>();
        for (var p : photos.findAll()) {
            out.put(p.getSlotId(), storage.urlFor(p.getS3Key()));
        }
        return out;
    }

    /** The admin's grouped slot grid, with current URLs merged in. */
    @Transactional(readOnly = true)
    public List<GroupView> groupsForAdmin() {
        var urls = urlsBySlot();
        return PhotoSlots.groups(pricing.list(), urls.keySet()).stream()
                .map(g -> new GroupView(g.title(), g.slots().stream()
                        .map(s -> new SlotView(s.id(), s.label(), urls.get(s.id()))).toList()))
                .toList();
    }

    @Transactional
    public PhotoView upload(String slotId, MultipartFile file) {
        if (!PhotoSlots.isValid(slotId, pricing::exists)) {
            throw new IllegalArgumentException("Unknown photo slot: " + slotId);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose an image to upload.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED.contains(contentType)) {
            throw new IllegalArgumentException("Please upload a JPEG, PNG, WebP or AVIF image.");
        }
        // Resize + re-encode before it ever reaches the bucket (full size plus smaller srcset renditions);
        // the original is never stored.
        List<ImageProcessor.Processed> renditions;
        try (var in = file.getInputStream()) {
            renditions = images.renditions(in);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("That file doesn't look like a readable image.");
        }
        var full = renditions.get(renditions.size() - 1);
        contentType = full.contentType();
        String key = storage.keyPrefix() + "site/" + slotId + "/" + UUID.randomUUID() + ".jpg";
        String widths = String.join(",", renditions.stream().map(r -> String.valueOf(r.width())).toList());
        try {
            for (var r : renditions) {
                String k = r == full ? key : PhotoStorage.variantKey(key, r.width());
                storage.put(k, new ByteArrayInputStream(r.bytes()), r.bytes().length, r.contentType());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        var existing = photos.findById(slotId).orElse(null);
        String oldKey = existing == null ? null : existing.getS3Key();
        String oldWidths = existing == null ? null : existing.getWidths();
        if (existing == null) {
            existing = new SitePhoto(slotId, key, contentType, widths);
        } else {
            existing.setS3Key(key);
            existing.setContentType(contentType);
            existing.setWidths(widths);
        }
        photos.save(existing);
        if (oldKey != null && !publish.publishedPhotoKeys().contains(oldKey)) {
            safeDelete(oldKey, oldWidths); // a draft-only object; a live one is cleaned up on Publish
        }
        return new PhotoView(slotId, storage.urlFor(key), contentType);
    }

    @Transactional
    public void clear(String slotId) {
        var existing = photos.findById(slotId).orElseThrow(() -> new NotFoundException("No photo in slot " + slotId));
        photos.delete(existing);
        if (!publish.publishedPhotoKeys().contains(existing.getS3Key())) {
            safeDelete(existing.getS3Key(), existing.getWidths());
        }
    }

    private void safeDelete(String key, String widths) {
        try {
            storage.deleteWithVariants(key, widths);
        } catch (Exception e) {
            // An orphaned object is a cleanup chore, not a failure the admin should see.
            log.warn("Could not delete old photo object {}", key, e);
        }
    }

}
