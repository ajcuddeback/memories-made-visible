package com.memoriesmadevisible.auth;

import com.memoriesmadevisible.common.NotFoundException;
import com.memoriesmadevisible.notification.BookingNotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

/**
 * Team management for owner/admin accounts. An admin may manage employees and other admins but
 * never the owner; nobody can remove themselves or the owner; the owner level can't be handed out here.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class AdminUsersController {

    private final AdminUserRepository users;
    private final PasswordEncoder encoder;
    private final BookingNotificationService mail;
    private final String siteUrl;

    public AdminUsersController(AdminUserRepository users, PasswordEncoder encoder, BookingNotificationService mail,
                                com.memoriesmadevisible.config.AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.mail = mail;
        this.siteUrl = props.notifications().siteUrl() == null ? "" : props.notifications().siteUrl().replaceAll("/+$", "");
    }

    public record Member(String name, String role, String email, AdminUser.Access access, boolean active,
                         boolean mustChangePassword, Instant createdAt, boolean isYou, boolean bookable) {}
    public record Create(@NotBlank @Size(max = 60) String name, @NotBlank @Size(max = 120) String role,
                         @NotBlank @Email @Size(max = 200) String email, @NotNull AdminUser.Access access) {}
    public record Update(@Size(max = 120) String role, @Email @Size(max = 200) String email, AdminUser.Access access, Boolean active,
                         Boolean bookable) {}

    @GetMapping
    public List<Member> list(Principal me) {
        return users.findAllByOrderByCreatedAtAscNameAsc().stream().map(u -> toMember(u, me)).toList();
    }

    /** Add a person: they get an email with a temporary password and must choose their own at first sign-in. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Member create(@Valid @RequestBody Create body, Principal me) {
        String name = body.name().strip();
        if (!name.matches("[A-Za-z][A-Za-z .'-]{0,59}")) throw new IllegalArgumentException("Names are letters, spaces and . ' - only.");
        if (users.findByNameIgnoreCase(name).isPresent()) throw new IllegalArgumentException("There's already an account called \"" + name + "\".");
        if (users.findByEmailIgnoreCase(body.email().strip()).isPresent()) throw new IllegalArgumentException("That email already belongs to an account.");
        if (body.access() == AdminUser.Access.OWNER) throw new IllegalArgumentException("There is only one owner account.");
        String temp = Passwords.temporary();
        var u = new AdminUser(name, body.role().strip(), encoder.encode(temp));
        u.setEmail(body.email().strip());
        u.setAccess(body.access());
        u.setMustChangePassword(true);
        users.save(u);
        sendInvite(u, temp, me.getName(), false);
        return toMember(u, me);
    }

    /** Resend the welcome email with a fresh temporary password (for someone who lost theirs). */
    @PostMapping("/{name}/invite")
    public Member reinvite(@PathVariable String name, Principal me) {
        var u = find(name);
        guard(u, me, "reset the password of");
        String temp = Passwords.temporary();
        u.setPasswordHash(encoder.encode(temp));
        u.setMustChangePassword(true);
        u.setReset(null, null);
        users.save(u);
        sendInvite(u, temp, me.getName(), true);
        return toMember(u, me);
    }

    @PatchMapping("/{name}")
    public Member update(@PathVariable String name, @Valid @RequestBody Update body, Principal me) {
        var u = find(name);
        boolean self = u.getName().equalsIgnoreCase(me.getName());
        if (!self) guard(u, me, "change");
        if (body.role() != null && !body.role().isBlank()) u.setRole(body.role().strip());
        if (body.email() != null && !body.email().isBlank()) {
            users.findByEmailIgnoreCase(body.email().strip()).filter(o -> !o.getName().equalsIgnoreCase(u.getName()))
                    .ifPresent(o -> { throw new IllegalArgumentException("That email already belongs to an account."); });
            u.setEmail(body.email().strip());
        }
        if (body.access() != null && !self) {
            if (body.access() == AdminUser.Access.OWNER || u.getAccess() == AdminUser.Access.OWNER) throw new IllegalArgumentException("The owner account can't be changed here.");
            u.setAccess(body.access());
        }
        if (body.bookable() != null) u.setBookable(body.bookable()); // the owner may flag themselves too
        if (body.active() != null && !self) {
            if (u.getAccess() == AdminUser.Access.OWNER) throw new IllegalArgumentException("The owner account can't be deactivated.");
            u.setActive(body.active());
        }
        users.save(u);
        return toMember(u, me);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name, Principal me) {
        var u = find(name);
        if (u.getName().equalsIgnoreCase(me.getName())) throw new IllegalArgumentException("You can't remove your own account.");
        guard(u, me, "remove");
        users.delete(u);
        return ResponseEntity.noContent().build();
    }

    private void guard(AdminUser target, Principal me, String verb) {
        if (target.getAccess() == AdminUser.Access.OWNER) throw new IllegalArgumentException("Only the owner can " + verb + " the owner account.");
        var actor = users.findByNameIgnoreCase(me.getName()).orElseThrow();
        if (actor.getAccess() == AdminUser.Access.ADMIN && target.getAccess() == AdminUser.Access.ADMIN
                && !target.getName().equalsIgnoreCase(actor.getName())) {
            throw new IllegalArgumentException("Only the owner can " + verb + " another admin.");
        }
    }

    private void sendInvite(AdminUser u, String temp, String by, boolean again) {
        String levelText = switch (u.getAccess()) {
            case OWNER -> "owner";
            case ADMIN -> "admin — you can do everything in the studio";
            case EMPLOYEE -> "employee — you can work sessions, the calendar and pop-up print sales";
        };
        mail.sendStaffEmail(u.getEmail(), again ? "Your new studio password" : "You're on the Memories Made Visible studio team",
                """
                Hi %s,

                %s Sign in with the name and temporary password below — you'll be asked to choose your own password straight away.

                Name: %s
                Temporary password: %s
                Access: %s
                """.formatted(u.getName(),
                        again ? by + " reset your studio password." : by + " added you to the studio admin.",
                        u.getName(), temp, levelText).strip(),
                "Open the studio", siteUrl + "/studio");
    }

    private AdminUser find(String name) {
        return users.findByNameIgnoreCase(name).orElseThrow(() -> new NotFoundException("No account called " + name));
    }

    private static Member toMember(AdminUser u, Principal me) {
        return new Member(u.getName(), u.getRole(), u.getEmail(), u.getAccess(), u.isActive(), u.isMustChangePassword(),
                u.getCreatedAt(), u.getName().equalsIgnoreCase(me.getName()), u.isBookable());
    }
}
