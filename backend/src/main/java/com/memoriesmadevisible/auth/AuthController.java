package com.memoriesmadevisible.auth;

import com.memoriesmadevisible.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AuthController {

    private final AdminUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RateLimiter loginLimiter = new RateLimiter(10, java.time.Duration.ofMinutes(10));

    public AuthController(AdminUserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public record LoginRequest(@NotBlank String name, @NotBlank String password) {}
    public record LoginResponse(String token, long expiresInSeconds, UserSummary user) {}
    public record UserSummary(String name, String role, String initial) {}

    /** The "Who's signing in?" picker on the admin login card. */
    @GetMapping("/users")
    public List<UserSummary> users() {
        return users.findAll().stream()
                .map(u -> new UserSummary(u.getName(), u.getRole(), u.initial()))
                .toList();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        if (!loginLimiter.tryAcquire(RateLimiter.clientKey(http))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many sign-in attempts. Try again in a few minutes."));
        }
        var user = users.findByNameIgnoreCase(req.name()).orElse(null);
        if (user == null || !encoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "That password doesn't match."));
        }
        return ResponseEntity.ok(new LoginResponse(jwt.issue(user), jwt.ttlSeconds(),
                new UserSummary(user.getName(), user.getRole(), user.initial())));
    }

    @GetMapping("/me")
    public UserSummary me(java.security.Principal principal) {
        var user = users.findByNameIgnoreCase(principal.getName()).orElseThrow();
        return new UserSummary(user.getName(), user.getRole(), user.initial());
    }
}
