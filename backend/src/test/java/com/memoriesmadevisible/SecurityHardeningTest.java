package com.memoriesmadevisible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoriesmadevisible.analytics.AnalyticsEventRepository;
import com.memoriesmadevisible.auth.AdminUserRepository;
import com.memoriesmadevisible.booking.BookingRepository;
import com.memoriesmadevisible.common.RateLimiter;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests from the security review. One test per confirmed finding, named after it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BookingRepository bookings;
    @Autowired AdminUserRepository users;
    @Autowired AnalyticsEventRepository analytics;

    @MockitoBean JavaMailSender mailSender;
    @MockitoBean S3Client s3Client;

    @BeforeEach
    void setUp() {
        bookings.deleteAll();
        org.mockito.Mockito.when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage((jakarta.mail.Session) null));
    }

    @AfterEach
    void tearDown() {
        // The Spring context (and its H2 database) is shared with the other test classes: leave the seeded accounts as found.
        users.findByNameIgnoreCase("Sam").ifPresent(users::delete);
        analytics.deleteAll();
    }

    // ── Finding: a JWT stayed valid after the password was changed or reset by an admin ──

    @Test
    void tokenIssuedBeforePasswordChangeOrAdminResetIsRevoked() throws Exception {
        String oldToken = login("Erica", "test-pass");
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + oldToken)).andExpect(status().isOk());

        // Changing the password hands back a fresh token and kills the old one.
        var res = mvc.perform(post("/api/admin/auth/password").header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current\":\"test-pass\",\"next\":\"brand-new-pass-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.name").value("Erica"))
                .andReturn().getResponse().getContentAsString();
        String newToken = json.readTree(res).get("token").asText();
        assertThat(newToken).isNotEqualTo(oldToken);

        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + oldToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/bookings").header("Authorization", "Bearer " + oldToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + newToken)).andExpect(status().isOk());

        // Put the seeded password back (the H2 database outlives this test).
        mvc.perform(post("/api/admin/auth/password").header("Authorization", "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current\":\"brand-new-pass-1\",\"next\":\"test-pass\"}"))
                .andExpect(status().isOk());

        // An admin re-inviting someone (fresh temporary password) also revokes their existing session.
        String owner = login("Austin", "test-pass");
        String samTemp = addSam(owner);
        String samToken = login("Sam", samTemp);
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + samToken)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/Sam/invite").header("Authorization", "Bearer " + owner)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + samToken)).andExpect(status().isForbidden());
    }

    // ── Finding: a temporary-password account could use every endpoint before choosing a password ──

    @Test
    void tempPasswordUserCannotUseOtherAdminEndpointsBeforeChangingIt() throws Exception {
        String owner = login("Austin", "test-pass");
        String temp = addSam(owner);
        var login = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Sam", "password", temp))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        String samToken = json.readTree(login).get("token").asText();
        var sam = "Bearer " + samToken;

        // Only "who am I" and "change my password" work until the temporary password is replaced.
        mvc.perform(get("/api/admin/auth/me").header("Authorization", sam)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/bookings").header("Authorization", sam)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/galleries").header("Authorization", sam)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/availability/blocks").header("Authorization", sam)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/availability/blocks").header("Authorization", sam)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + LocalDate.now().plusDays(5) + "\"}"))
                .andExpect(status().isForbidden());

        var res = mvc.perform(post("/api/admin/auth/password").header("Authorization", sam)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("current", temp, "next", "sams-own-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();
        var samAfter = "Bearer " + json.readTree(res).get("token").asText();
        mvc.perform(get("/api/admin/bookings").header("Authorization", samAfter)).andExpect(status().isOk());
    }

    // ── Finding: login/booking/forgot rate limits keyed on a client-supplied X-Forwarded-For header ──

    @Test
    void rateLimitKeyIgnoresSpoofedForwardedForHeader() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.9");
        req.addHeader("X-Forwarded-For", "198.51.100.1");
        // The key is the address the container resolved (server.forward-headers-strategy=native handles trusted
        // proxies), never a header anyone can set — otherwise every request could pick a fresh bucket.
        assertThat(RateLimiter.clientKey(req)).isEqualTo("203.0.113.9");

        var limiter = new RateLimiter(2, java.time.Duration.ofMinutes(1));
        for (int i = 0; i < 2; i++) assertThat(limiter.tryAcquire(RateLimiter.clientKey(req))).isTrue();
        var spoofed = new MockHttpServletRequest();
        spoofed.setRemoteAddr("203.0.113.9");
        spoofed.addHeader("X-Forwarded-For", "10.0.0." + 42);
        assertThat(limiter.tryAcquire(RateLimiter.clientKey(spoofed))).isFalse();
    }

    // ── Finding: a customer's name went into an email Subject header unsanitised (CR/LF header injection) ──

    @Test
    void customerNameCannotInjectEmailHeaders() throws Exception {
        var body = Map.of(
                "sessionType", "seniors", "packageName", "Mini",
                "name", "Taylor\r\nBcc: evil@example.com\r\nX-Injected: yes",
                "email", "taylor@example.com",
                "date", LocalDate.now().plusDays(20).toString(), "time", "10:00");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(2)).send(captor.capture());
        for (MimeMessage m : captor.getAllValues()) {
            m.saveChanges();
            var out = new ByteArrayOutputStream();
            m.writeTo(out);
            // Re-read the wire form the way a mail server would and make sure no header was smuggled in.
            var parsed = new MimeMessage(null, new java.io.ByteArrayInputStream(out.toByteArray()));
            assertThat(parsed.getHeader("Bcc")).as("smuggled Bcc header").isNull();
            assertThat(parsed.getHeader("X-Injected")).as("smuggled custom header").isNull();
            assertThat(parsed.getSubject()).doesNotContain("\r").doesNotContain("\n");
        }
    }

    // ── Finding: the public analytics endpoint parsed request bodies of any size ──

    @Test
    void analyticsDropsOversizedBatches() throws Exception {
        long before = analytics.count();
        var ev = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            if (i > 0) ev.append(',');
            ev.append("{\"visitor\":\"v").append(i).append("\",\"session\":\"s").append(i)
              .append("\",\"kind\":\"view\",\"path\":\"/\",\"device\":\"desktop\"}");
        }
        String pad = "x".repeat(2_000_000);
        String big = "{\"pad\":\"" + pad + "\",\"events\":[" + ev + "]}";
        mvc.perform(post("/api/analytics").contentType(MediaType.APPLICATION_JSON).content(big))
                .andExpect(status().isNoContent());
        assertThat(analytics.count()).as("nothing stored from a multi-megabyte batch").isEqualTo(before);

        // A normal batch is still recorded.
        mvc.perform(post("/api/analytics").contentType(MediaType.APPLICATION_JSON).content("{\"events\":[" + ev + "]}"))
                .andExpect(status().isNoContent());
        assertThat(analytics.count()).isEqualTo(before + 50);
    }

    // ── helpers ──

    private String login(String name, String password) throws Exception {
        var res = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    /** Owner adds employee "Sam"; returns the temporary password from the captured invite email. */
    private String addSam(String ownerToken) throws Exception {
        int already = org.mockito.Mockito.mockingDetails(mailSender).getInvocations().size();
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Sam", "role", "Assistant", "email", "sam@test.local", "access", "EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).atLeastOnce()).send(captor.capture());
        for (MimeMessage m : captor.getAllValues()) {
            String text = bodies(m)[0];
            if (text == null) continue;
            var mm = Pattern.compile("Temporary password: (\\S+)").matcher(text);
            if (mm.find() && m.getSubject().contains("studio team")) return mm.group(1);
        }
        throw new AssertionError("No invite email captured (" + already + " earlier invocations)");
    }

    static String[] bodies(MimeMessage m) throws Exception {
        m.saveChanges();
        String[] out = new String[2];
        walk(m, out);
        return out;
    }

    private static void walk(Part p, String[] out) throws Exception {
        Object c = p.getContent();
        if (c instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) walk(mp.getBodyPart(i), out);
        } else if (p.isMimeType("text/html")) {
            out[1] = String.valueOf(c);
        } else if (p.isMimeType("text/plain")) {
            out[0] = String.valueOf(c);
        }
    }
}
