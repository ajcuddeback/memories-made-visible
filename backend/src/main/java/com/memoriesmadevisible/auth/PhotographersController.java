package com.memoriesmadevisible.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public: the photographers a customer may choose on the booking page (empty = the studio books as one). */
@RestController
@RequestMapping("/api/photographers")
public class PhotographersController {

    private final AdminUserRepository users;

    public PhotographersController(AdminUserRepository users) {
        this.users = users;
    }

    public record Photographer(String name, String role) {}

    @GetMapping
    public List<Photographer> list() {
        return users.findAllByActiveTrueAndBookableTrueOrderByNameAsc().stream()
                .map(u -> new Photographer(u.getName(), u.getRole())).toList();
    }
}
