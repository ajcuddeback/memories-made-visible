package com.memoriesmadevisible.photo;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalises every uploaded photo into web-ready JPEGs: longest edge capped, EXIF orientation applied, metadata
 * stripped, sensible quality, and smaller renditions for {@code srcset} so a phone never downloads the desktop
 * hero. This is what keeps Largest Contentful Paint honest no matter what the camera produced.
 */
@Component
public class ImageProcessor {

    /** Enough for a full-width hero on a 2× display; everything on the site renders smaller. */
    public static final int MAX_EDGE = 1800;
    /** Smaller widths produced alongside the full-size image (only those narrower than it). */
    public static final int[] RENDITION_WIDTHS = {480, 960};
    private static final float QUALITY = 0.84f;

    public record Processed(byte[] bytes, String contentType, int width, int height) {}

    public Processed toWebJpeg(InputStream source) throws IOException {
        var image = Thumbnails.of(source)
                .size(MAX_EDGE, MAX_EDGE)      // fits within the box, never upscales below
                .keepAspectRatio(true)
                .useExifOrientation(true)
                .outputFormat("jpg")
                .outputQuality(QUALITY)
                .asBufferedImage();
        return encode(image);
    }

    /** The full-size image plus each narrower rendition, ordered smallest first. */
    public List<Processed> renditions(InputStream source) throws IOException {
        var full = Thumbnails.of(source)
                .size(MAX_EDGE, MAX_EDGE)
                .keepAspectRatio(true)
                .useExifOrientation(true)
                .outputFormat("jpg")
                .outputQuality(QUALITY)
                .asBufferedImage();
        List<Processed> out = new ArrayList<>();
        for (int w : RENDITION_WIDTHS) {
            if (w >= full.getWidth()) continue;
            var small = Thumbnails.of(full).width(w).keepAspectRatio(true).outputFormat("jpg").outputQuality(QUALITY).asBufferedImage();
            out.add(encode(small));
        }
        out.add(encode(full));
        return out;
    }

    private static Processed encode(BufferedImage image) throws IOException {
        var out = new ByteArrayOutputStream();
        Thumbnails.of(image).scale(1).outputFormat("jpg").outputQuality(QUALITY).toOutputStream(out);
        return new Processed(out.toByteArray(), "image/jpeg", image.getWidth(), image.getHeight());
    }
}
