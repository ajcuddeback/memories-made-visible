package com.memoriesmadevisible.photo;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Normalises every uploaded photo into a web-ready JPEG: longest edge capped,
 * EXIF orientation applied, metadata stripped, sensible quality. This is what
 * keeps Largest Contentful Paint honest no matter what the camera produced.
 */
@Component
public class ImageProcessor {

    /** Enough for a full-width hero on a 2× display; everything on the site renders smaller. */
    public static final int MAX_EDGE = 1800;
    private static final float QUALITY = 0.84f;

    public record Processed(byte[] bytes, String contentType, int width, int height) {}

    public Processed toWebJpeg(InputStream source) throws IOException {
        var out = new ByteArrayOutputStream();
        var image = Thumbnails.of(source)
                .size(MAX_EDGE, MAX_EDGE)      // fits within the box, never upscales below
                .keepAspectRatio(true)
                .useExifOrientation(true)
                .outputFormat("jpg")
                .outputQuality(QUALITY)
                .asBufferedImage();
        Thumbnails.of(image).scale(1).outputFormat("jpg").outputQuality(QUALITY).toOutputStream(out);
        return new Processed(out.toByteArray(), "image/jpeg", image.getWidth(), image.getHeight());
    }
}
