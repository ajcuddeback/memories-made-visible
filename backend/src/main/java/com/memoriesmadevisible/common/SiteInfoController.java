package com.memoriesmadevisible.common;

import com.memoriesmadevisible.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: how to reach the studio directly, for people who'd rather not use the request form. */
@RestController
@RequestMapping("/api/site")
public class SiteInfoController {

    private final AppProperties.Contact contact;

    public SiteInfoController(AppProperties props) {
        this.contact = props.contact();
    }

    public record Contact(String email, String phone, String instagram, String location) {}

    @GetMapping("/contact")
    public Contact contact() {
        return new Contact(blankToNull(contact.email()), blankToNull(contact.phone()), blankToNull(contact.instagram()),
                blankToNull(contact.location()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
