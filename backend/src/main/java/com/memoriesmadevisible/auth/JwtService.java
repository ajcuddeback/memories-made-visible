package com.memoriesmadevisible.auth;

import com.memoriesmadevisible.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(AppProperties props) {
        var secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofHours(props.jwt().ttlHours() > 0 ? props.jwt().ttlHours() : 12);
    }

    public String issue(AdminUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getName())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /** Returns the subject (admin name) when the token is valid and unexpired. */
    public Optional<String> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
