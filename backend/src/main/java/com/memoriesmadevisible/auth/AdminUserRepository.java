package com.memoriesmadevisible.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, String> {
    Optional<AdminUser> findByNameIgnoreCase(String name);
    Optional<AdminUser> findByEmailIgnoreCase(String email);
    Optional<AdminUser> findByResetToken(String token);
    List<AdminUser> findAllByActiveTrueOrderByNameAsc();
    List<AdminUser> findAllByOrderByCreatedAtAscNameAsc();
    boolean existsByAccess(AdminUser.Access access);
    List<AdminUser> findAllByActiveTrueAndBookableTrueOrderByNameAsc();
}
