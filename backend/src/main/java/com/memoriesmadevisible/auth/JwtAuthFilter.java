package com.memoriesmadevisible.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** What someone who still has a temporary password may call (see AuthController). */
    private static final java.util.Set<String> PASSWORD_SETUP = java.util.Set.of("/api/admin/auth/me", "/api/admin/auth/password");

    private final JwtService jwt;
    private final AdminUserRepository users;

    public JwtAuthFilter(JwtService jwt, AdminUserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwt.verify(header.substring(7))
                    .flatMap(v -> users.findByNameIgnoreCase(v.name()).filter(u -> jwt.matchesPassword(v, u)))
                    .filter(AdminUser::isActive)
                    // A temporary password only opens the door to choosing a real one.
                    .filter(user -> !user.isMustChangePassword() || PASSWORD_SETUP.contains(request.getRequestURI()))
                    .ifPresent(user -> {
                        // ROLE_STAFF for anyone signed in; ROLE_<access> for what they may do.
                        var auth = new UsernamePasswordAuthenticationToken(user.getName(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_STAFF"),
                                        new SimpleGrantedAuthority("ROLE_" + user.getAccess().name())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}
