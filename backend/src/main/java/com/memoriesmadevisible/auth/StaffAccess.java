package com.memoriesmadevisible.auth;

import org.springframework.stereotype.Component;

import java.security.Principal;

/** Who is calling, and whether their view should be limited to assigned sessions. */
@Component
public class StaffAccess {

    private final AdminUserRepository users;

    public StaffAccess(AdminUserRepository users) {
        this.users = users;
    }

    public AdminUser current(Principal principal) {
        return users.findByNameIgnoreCase(principal.getName()).orElseThrow();
    }

    /** Employees see only what they're assigned to; owner/admin see everything. */
    public boolean limited(Principal principal) {
        return current(principal).getAccess() == AdminUser.Access.EMPLOYEE;
    }
}
