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

    /** Owner/admin, or the booking's lead photographer: may quote, approve, decline, email and deliver it. */
    public boolean mayRun(Principal principal, com.memoriesmadevisible.booking.Booking booking) {
        if (!limited(principal)) return true;
        return booking != null && booking.getLead() != null && booking.getLead().equalsIgnoreCase(principal.getName());
    }

    public void requireMayRun(Principal principal, com.memoriesmadevisible.booking.Booking booking) {
        if (!mayRun(principal, booking)) throw new org.springframework.security.access.AccessDeniedException("Only the lead photographer or an admin can do that.");
    }
}
