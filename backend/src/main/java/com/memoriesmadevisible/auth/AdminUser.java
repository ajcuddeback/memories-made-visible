package com.memoriesmadevisible.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A studio staff account. {@code role} is the job title shown in the admin; {@link Access} is what they may do. */
@Entity
@Table(name = "admin_user")
public class AdminUser {

    /** OWNER: the one main account; ADMIN: everything else; EMPLOYEE: day-to-day only (sessions pipeline, calendar blocks, prints). */
    public enum Access { OWNER, ADMIN, EMPLOYEE }

    /** Display name doubles as the login. */
    @Id
    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 120)
    private String role;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 200)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Access access = Access.ADMIN;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "reset_token", length = 80)
    private String resetToken;

    @Column(name = "reset_expires")
    private Instant resetExpires;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected AdminUser() {}

    public AdminUser(String name, String role, String passwordHash) {
        this.name = name;
        this.role = role;
        this.passwordHash = passwordHash;
    }

    public String getName() { return name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public Access getAccess() { return access; }
    public void setAccess(Access v) { this.access = v == null ? Access.EMPLOYEE : v; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean v) { this.mustChangePassword = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public String getResetToken() { return resetToken; }
    public Instant getResetExpires() { return resetExpires; }
    public void setReset(String token, Instant expires) { this.resetToken = token; this.resetExpires = expires; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean canManage() { return access == Access.OWNER || access == Access.ADMIN; }

    public String initial() {
        return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
    }
}
