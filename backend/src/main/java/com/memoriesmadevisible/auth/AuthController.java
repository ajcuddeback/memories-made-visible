package com.memoriesmadevisible.auth;

import com.memoriesmadevisible.common.RateLimiter;
import com.memoriesmadevisible.notification.BookingNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AuthController {

    private final AdminUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final BookingNotificationService mail;
    private final String siteUrl;
    /** Sign-in attempts per client per 10 minutes (app.login-rate-limit; tests raise it). */
    private final RateLimiter loginLimiter;
    private final RateLimiter forgotLimiter = new RateLimiter(5, Duration.ofMinutes(15));

    public AuthController(AdminUserRepository users, PasswordEncoder encoder, JwtService jwt, BookingNotificationService mail,
                          com.memoriesmadevisible.config.AppProperties props,
                          @org.springframework.beans.factory.annotation.Value("${app.login-rate-limit:10}") int loginLimit) {
        this.loginLimiter = new RateLimiter(loginLimit, Duration.ofMinutes(10));
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.mail = mail;
        this.siteUrl = props.notifications().siteUrl() == null ? "" : props.notifications().siteUrl().replaceAll("/+$", "");
    }

    public record LoginRequest(@NotBlank String name, @NotBlank String password) {}
    public record LoginResponse(String token, long expiresInSeconds, UserSummary user) {}
    /** What the studio shows about the signed-in person. {@code access} decides which sections they see. */
    public record UserSummary(String name, String role, String initial, AdminUser.Access access, boolean mustChangePassword) {}
    public record PickerEntry(String name, String role, String initial) {}
    public record ChangePassword(@NotBlank String current, @NotBlank @Size(min = 8, max = 100) String next) {}
    public record Forgot(@NotBlank @Email String email) {}
    public record Reset(@NotBlank String token, @NotBlank @Size(min = 8, max = 100) String password) {}

    /** The "Who's signing in?" picker on the admin login card (active accounts only). */
    @GetMapping("/users")
    public List<PickerEntry> users() {
        return users.findAllByActiveTrueOrderByNameAsc().stream()
                .map(u -> new PickerEntry(u.getName(), u.getRole(), u.initial()))
                .toList();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        if (!loginLimiter.tryAcquire(RateLimiter.clientKey(http))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many sign-in attempts. Try again in a few minutes."));
        }
        var user = users.findByNameIgnoreCase(req.name()).orElse(null);
        if (user == null || !user.isActive() || !encoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "That password doesn't match."));
        }
        return ResponseEntity.ok(new LoginResponse(jwt.issue(user), jwt.ttlSeconds(), summary(user)));
    }

    @GetMapping("/me")
    public UserSummary me(Principal principal) {
        return summary(users.findByNameIgnoreCase(principal.getName()).orElseThrow());
    }

    /**
     * Change your own password (also how a temporary password is replaced at first sign-in). Every token issued
     * against the old password stops working, so the response carries a fresh one for this session.
     */
    @PostMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePassword body, Principal principal) {
        var user = users.findByNameIgnoreCase(principal.getName()).orElseThrow();
        if (!encoder.matches(body.current(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Your current password doesn't match."));
        }
        if (encoder.matches(body.next(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Choose a password you haven't used here before."));
        }
        user.setPasswordHash(encoder.encode(body.next()));
        user.setMustChangePassword(false);
        user.setReset(null, null);
        users.save(user);
        return ResponseEntity.ok(new LoginResponse(jwt.issue(user), jwt.ttlSeconds(), summary(user)));
    }

    /** Public: email a reset link. Always answers 204 so the form can't be used to find out who has an account. */
    @PostMapping("/forgot")
    public ResponseEntity<Void> forgot(@Valid @RequestBody Forgot body, HttpServletRequest http) {
        if (forgotLimiter.tryAcquire(RateLimiter.clientKey(http))) {
            users.findByEmailIgnoreCase(body.email().strip()).filter(AdminUser::isActive).ifPresent(user -> {
                String token = Passwords.token();
                user.setReset(token, Instant.now().plus(Duration.ofHours(2)));
                users.save(user);
                mail.sendStaffEmail(user.getEmail(), "Reset your studio password",
                        """
                        Hi %s,

                        Someone asked to reset the password for your Memories Made Visible studio account. If that was you, use the button below — the link works for the next 2 hours.

                        If it wasn't you, you can ignore this email; your password hasn't changed.
                        """.formatted(user.getName()).strip(),
                        "Set a new password", siteUrl + "/studio?reset=" + token);
            });
        }
        return ResponseEntity.noContent().build();
    }

    /** Public: finish a reset from the emailed link. */
    @PostMapping("/reset")
    public ResponseEntity<?> reset(@Valid @RequestBody Reset body) {
        var user = users.findByResetToken(body.token().strip()).orElse(null);
        if (user == null || user.getResetExpires() == null || user.getResetExpires().isBefore(Instant.now()) || !user.isActive()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "That reset link has expired. Ask for a new one."));
        }
        user.setPasswordHash(encoder.encode(body.password()));
        user.setMustChangePassword(false);
        user.setReset(null, null);
        users.save(user);
        return ResponseEntity.ok(new LoginResponse(jwt.issue(user), jwt.ttlSeconds(), summary(user)));
    }

    static UserSummary summary(AdminUser u) {
        return new UserSummary(u.getName(), u.getRole(), u.initial(), u.getAccess(), u.isMustChangePassword());
    }
}
