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
        S3 s3,
        Contact contact
) {

    /** How customers reach the studio directly (shown on the booking page, about page and footer). */
    public record Contact(String email, String phone, String instagram, String location) {}

    /** {@code owner} names the single OWNER account; the rest are ADMIN until changed in the studio. */
    public record Admin(List<User> users, String owner) {
        public record User(String name, String role, String password, String email) {}
    }

    /** Secret must be at least 32 bytes; token lifetime in hours. */
    public record Jwt(String secret, long ttlHours) {}

    public record Cors(List<String> allowedOrigins) {}

    /**
     * {@code to} is the studio inbox that receives every new booking request
     * (bookings@), {@code from} is the internal sender (admin@), and
     * {@code customerFrom} is the address customers see on their acknowledgement.
     */
    public record Notifications(String from, String to, String customerFrom, boolean sendCustomerConfirmation,
                                String siteUrl) {}

    /**
     * {@code publicBaseUrl} (bucket website / CloudFront) is optional — when it
     * is blank the API hands out short-lived presigned GET links instead.
     */
    public record S3(String bucket, String region, String endpoint, String publicBaseUrl, String keyPrefix,
                     boolean pathStyleAccess) {}
}
