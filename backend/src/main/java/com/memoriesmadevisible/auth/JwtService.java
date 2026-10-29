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

    /** A valid, unexpired token: who it is for and which password it was issued against. */
    public record Verified(String name, String passwordStamp) {}

    public String issue(AdminUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getName())
                .claim("role", user.getRole())
                // Tokens die with the password: changing or resetting it (or an admin re-invite) ends every open session.
                .claim("pwd", stamp(user))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /** Returns the subject (admin name) and password stamp when the token is valid and unexpired. */
    public Optional<Verified> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (claims.getSubject() == null) return Optional.empty();
            return Optional.of(new Verified(claims.getSubject(), claims.get("pwd", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Does this token still match the account's current password? */
    public boolean matchesPassword(Verified v, AdminUser user) {
        return v.passwordStamp() != null && java.security.MessageDigest.isEqual(
                v.passwordStamp().getBytes(StandardCharsets.UTF_8), stamp(user).getBytes(StandardCharsets.UTF_8));
    }

    /** A short, non-reversible fingerprint of the password hash (the hash itself never leaves the server). */
    static String stamp(AdminUser user) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(String.valueOf(user.getPasswordHash()).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d, 0, 12);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
