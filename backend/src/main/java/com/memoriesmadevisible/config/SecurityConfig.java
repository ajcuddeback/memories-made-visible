package com.memoriesmadevisible.config;

import com.memoriesmadevisible.auth.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties props;
    private final JwtAuthFilter jwtAuthFilter;
    /** The port actuator endpoints are served on, learned when that server starts (0 = none, or same as the API). */
    private volatile int managementPort;

    public SecurityConfig(AppProperties props, JwtAuthFilter jwtAuthFilter) {
        this.props = props;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @org.springframework.context.event.EventListener
    void onServerStarted(org.springframework.boot.web.context.WebServerInitializedEvent event) {
        if ("management".equals(event.getApplicationContext().getServerNamespace())) {
            managementPort = event.getWebServer().getPort();
        }
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Login + the "who's signing in" picker are open; everything else under /api/admin needs a token.
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login", "/api/admin/auth/forgot", "/api/admin/auth/reset").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/auth/users").permitAll()
                        // Any active staff account gets in; @PreAuthorize on the controllers decides what each level may do.
                        .requestMatchers("/api/admin/**").hasRole("STAFF")
                        .requestMatchers("/api/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/livez", "/readyz").permitAll()
                        // Metrics are open only on the management port, which is never published to the internet.
                        .requestMatchers(r -> managementPort > 0 && r.getLocalPort() == managementPort
                                && r.getRequestURI().startsWith("/actuator/")).permitAll()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
