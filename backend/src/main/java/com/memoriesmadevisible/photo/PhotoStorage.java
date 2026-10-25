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
