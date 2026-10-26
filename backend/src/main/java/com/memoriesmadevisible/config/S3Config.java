package com.memoriesmadevisible.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Credentials come from the default AWS provider chain (env vars, profile,
 * instance role). {@code app.s3.endpoint} + path-style lets local dev point
 * at LocalStack (or any S3-compatible store) without any code change.
 */
@Configuration
public class S3Config {

    @Bean
    S3Client s3Client(AppProperties props) {
        var s3 = props.s3();
        var builder = S3Client.builder()
                .region(Region.of(s3.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.pathStyleAccess())
                        .build());
        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.endpoint()));
        }
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(AppProperties props) {
        var s3 = props.s3();
        var builder = S3Presigner.builder()
                .region(Region.of(s3.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.pathStyleAccess())
                        .build());
        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.endpoint()));
        }
        return builder.build();
    }
}
