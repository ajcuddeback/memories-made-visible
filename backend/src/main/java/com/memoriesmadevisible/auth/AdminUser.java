package com.memoriesmadevisible.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_user")
public class AdminUser {

    /** Display name doubles as the login — the studio has two people. */
    @Id
    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 120)
    private String role;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

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

    public String initial() {
        return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
    }
}
