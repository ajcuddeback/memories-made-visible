package com.memoriesmadevisible.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * All studio-specific settings live under the {@code app.*} prefix so the
 * deployment only has to think about one block of environment variables.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Admin admin,
        Jwt jwt,
        Cors cors,
        Notifications notifications,
        S3 s3
) {

    public record Admin(List<User> users) {
        public record User(String name, String role, String password) {}
    }

    /** Secret must be at least 32 bytes; token lifetime in hours. */
    public record Jwt(String secret, long ttlHours) {}

    public record Cors(List<String> allowedOrigins) {}

    /**
     * {@code to} is the inbox that receives every new booking request. It is
     * intentionally unset by default so a mis-deploy can never mail a stranger.
     */
    public record Notifications(String from, String to, boolean sendCustomerConfirmation, String siteUrl) {}

    /**
     * {@code publicBaseUrl} (bucket website / CloudFront) is optional — when it
     * is blank the API hands out short-lived presigned GET links instead.
     */
    public record S3(String bucket, String region, String endpoint, String publicBaseUrl, String keyPrefix,
                     boolean pathStyleAccess) {}
}
