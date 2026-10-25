package com.memoriesmadevisible.auth;

import com.memoriesmadevisible.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Admin accounts are declared in configuration (name, role, password) and
 * upserted on boot. Changing a password in the env and restarting is the whole
 * password-reset story — right-sized for a two-person studio.
 */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final AppProperties props;
    private final AdminUserRepository users;
    private final PasswordEncoder encoder;

    public AdminUserSeeder(AppProperties props, AdminUserRepository users, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        var configured = props.admin() == null ? null : props.admin().users();
        if (configured == null || configured.isEmpty()) {
            log.warn("No admin users configured (app.admin.users) — the studio admin cannot be signed in to.");
            return;
        }
        for (var u : configured) {
            if (u.password() == null || u.password().isBlank()) {
                log.warn("Admin user '{}' has no password set; skipping.", u.name());
                continue;
            }
            var existing = users.findByNameIgnoreCase(u.name()).orElse(null);
            if (existing == null) {
                users.save(new AdminUser(u.name(), u.role(), encoder.encode(u.password())));
                log.info("Created admin user '{}'.", u.name());
            } else {
                boolean changed = false;
                if (!encoder.matches(u.password(), existing.getPasswordHash())) {
                    existing.setPasswordHash(encoder.encode(u.password()));
                    changed = true;
                }
                if (u.role() != null && !u.role().equals(existing.getRole())) {
                    existing.setRole(u.role());
                    changed = true;
                }
                if (changed) {
                    users.save(existing);
                    log.info("Updated admin user '{}'.", u.name());
                }
            }
        }
    }
}
