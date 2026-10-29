package com.memoriesmadevisible.photo;

import com.memoriesmadevisible.config.AppProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/** Thin S3 wrapper: put / delete / resolve a browser-loadable URL. */
@Component
public class PhotoStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final AppProperties.S3 cfg;

    public PhotoStorage(S3Client s3, S3Presigner presigner, AppProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.cfg = props.s3();
    }

    public void put(String key, InputStream data, long size, String contentType) throws IOException {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(cfg.bucket())
                        .key(key)
                        .contentType(contentType)
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromInputStream(data, size));
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(cfg.bucket()).key(key).build());
    }

    /** The full-size object and every narrower rendition recorded for it. */
    public void deleteWithVariants(String key, String widths) {
        for (String k : allKeys(key, widths)) delete(k);
    }

    /** {@code site/x/uuid.jpg} + width 480 → {@code site/x/uuid-480.jpg}. */
    public static String variantKey(String key, int width) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? key + "-" + width : key.substring(0, dot) + "-" + width + key.substring(dot);
    }

    /** Every object key a photo occupies: the renditions (all widths but the last) and the full-size key. */
    public static java.util.List<String> allKeys(String key, String widths) {
        var ws = parseWidths(widths);
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < ws.size(); i++) keys.add(variantKey(key, ws.get(i)));
        keys.add(key);
        return keys;
    }

    public static java.util.List<Integer> parseWidths(String widths) {
        if (widths == null || widths.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(widths.split(",")).map(String::strip).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
    }

    /** What the public site needs for an {@code <img>}: a src, and a srcset when renditions exist. */
    public PhotoDtos.PhotoUrls urlsFor(String key, String widths) {
        var ws = parseWidths(widths);
        if (ws.size() < 2) return new PhotoDtos.PhotoUrls(urlFor(key), null, ws.isEmpty() ? null : ws.get(0));
        StringBuilder srcset = new StringBuilder();
        for (int i = 0; i < ws.size(); i++) {
            String k = i + 1 < ws.size() ? variantKey(key, ws.get(i)) : key;
            if (i > 0) srcset.append(", ");
            srcset.append(urlFor(k)).append(' ').append(ws.get(i)).append('w');
        }
        return new PhotoDtos.PhotoUrls(urlFor(key), srcset.toString(), ws.get(ws.size() - 1));
    }

    /**
     * With a public base URL (bucket website or CloudFront) the link is plain and
     * cacheable; without one we fall back to a presigned GET good for 12 hours.
     */
    public String urlFor(String key) {
        if (cfg.publicBaseUrl() != null && !cfg.publicBaseUrl().isBlank()) {
            return cfg.publicBaseUrl().replaceAll("/+$", "") + "/" + key;
        }
        var req = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(12))
                .getObjectRequest(GetObjectRequest.builder().bucket(cfg.bucket()).key(key).build())
                .build();
        return presigner.presignGetObject(req).url().toString();
    }

    public String keyPrefix() {
        String p = cfg.keyPrefix() == null ? "" : cfg.keyPrefix().trim();
        if (p.isEmpty()) return "";
        return p.replaceAll("^/+|/+$", "") + "/";
    }
}
