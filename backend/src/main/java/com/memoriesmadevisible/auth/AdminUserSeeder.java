package com.memoriesmadevisible.auth;

import com.memoriesmadevisible.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The first accounts come from configuration (name, role, password); they are created
 * once and never overwritten afterwards, because people change their own passwords in
 * the studio now. {@code app.admin.owner} names the one OWNER account.
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
                var created = new AdminUser(u.name(), u.role(), encoder.encode(u.password()));
                created.setEmail(u.email());
                users.save(created);
                log.info("Created staff account '{}'.", u.name());
            } else if (existing.getEmail() == null && u.email() != null && !u.email().isBlank()) {
                existing.setEmail(u.email());
                users.save(existing);
            }
        }
        // Exactly one owner: the configured name, else the first configured account.
        if (!users.existsByAccess(AdminUser.Access.OWNER)) {
            String ownerName = props.admin().owner() == null || props.admin().owner().isBlank()
                    ? configured.get(0).name() : props.admin().owner();
            users.findByNameIgnoreCase(ownerName).ifPresentOrElse(o -> {
                o.setAccess(AdminUser.Access.OWNER);
                users.save(o);
                log.info("'{}' is the studio owner account.", o.getName());
            }, () -> log.warn("app.admin.owner '{}' is not a configured user; no owner account set.", ownerName));
        }
    }
}
