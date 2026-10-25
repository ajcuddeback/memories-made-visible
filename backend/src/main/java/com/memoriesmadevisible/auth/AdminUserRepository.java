package com.memoriesmadevisible.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, String> {
    Optional<AdminUser> findByNameIgnoreCase(String name);
}
