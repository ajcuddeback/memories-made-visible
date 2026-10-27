package com.memoriesmadevisible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoriesmadevisible.booking.BookingRepository;
import com.memoriesmadevisible.booking.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BookingRepository bookings;

    @MockitoBean JavaMailSender mailSender;
    @MockitoBean S3Client s3Client;

    @BeforeEach
    void clean() {
        bookings.deleteAll();
        // The mocked sender must still hand out real MimeMessages for the helper to fill.
        org.mockito.Mockito.when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage((jakarta.mail.Session) null));
    }

    /** Walk a multipart message and return its text/plain and text/html bodies. */
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

    private static MimeMessage bySubject(ArgumentCaptor<MimeMessage> captor, String fragment) throws Exception {
        for (MimeMessage m : captor.getAllValues()) {
            if (m.getSubject() != null && m.getSubject().contains(fragment)) return m;
        }
        throw new AssertionError("No email with subject containing: " + fragment);
    }

    private static String[] addresses(jakarta.mail.Address[] a) {
        if (a == null) return new String[0];
        return java.util.Arrays.stream(a).map(x -> ((jakarta.mail.internet.InternetAddress) x).getAddress()).toArray(String[]::new);
    }

    @Test
    void publicRequestCreatesPendingBookingAndEmailsTheStudio() throws Exception {
        var body = Map.of(
                "sessionType", "weddings",
                "name", "Taylor Reyes",
                "email", "taylor@example.com",
                "phone", "(863) 555-0100",
                "location", "Winter Haven",
                "notes", "About 120 guests.",
                "date", LocalDate.now().plusDays(30).toString(),
                "time", "2:00 PM");

        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sessionTypeName").value("Weddings"))
                .andExpect(jsonPath("$.quote").value(800));

        assertThat(bookings.countByStatus(BookingStatus.PENDING)).isEqualTo(1);

        // Notification is async; wait for it. Studio email first, then the customer copy.
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(2)).send(captor.capture());
        var studio = captor.getAllValues().get(0);
        assertThat(addresses(studio.getRecipients(MimeMessage.RecipientType.TO))).containsExactly("bookings@test.local");
        assertThat(addresses(studio.getFrom())[0]).isEqualTo("admin@test.local");
        assertThat(addresses(studio.getReplyTo())[0]).isEqualTo("taylor@example.com");
        assertThat(studio.getSubject()).contains("Weddings").contains("Taylor Reyes");
        var studioBodies = bodies(studio);
        assertThat(studioBodies[0]).contains("Winter Haven").contains("About 120 guests.").contains("/studio");
        assertThat(studioBodies[1]).contains("<table").contains("Winter Haven").contains("Open in studio admin");
        var customer = captor.getAllValues().get(1);
        assertThat(addresses(customer.getRecipients(MimeMessage.RecipientType.TO))).containsExactly("taylor@example.com");
        assertThat(addresses(customer.getFrom())[0]).isEqualTo("admin@test.local");
        assertThat(addresses(customer.getReplyTo())[0]).isEqualTo("bookings@test.local");
        assertThat(bodies(customer)[1]).contains("Hi Taylor,").doesNotContain("<script");
    }

    @Test
    void honeypotSubmissionsAreSilentlyDropped() throws Exception {
        var body = Map.of(
                "sessionType", "families", "name", "Bot", "email", "bot@example.com",
                "date", LocalDate.now().plusDays(3).toString(), "time", "9:00 AM", "website", "http://spam");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated());
        assertThat(bookings.count()).isZero();
    }

    @Test
    void validationRejectsMissingFields() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.sessionType").exists());
    }

    @Test
    void adminEndpointsRequireAToken_andApprovalFlowWorks() throws Exception {
        mvc.perform(get("/api/admin/bookings")).andExpect(status().isForbidden());

        var token = login("Erica", "test-pass");
        var auth = "Bearer " + token;

        var created = mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "sessionType", "seniors", "name", "Ava", "email", "ava@example.com",
                        "date", LocalDate.now().plusDays(10).toString(), "time", "Golden hour"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        mvc.perform(patch("/api/admin/bookings/" + id + "/quote").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quote\":250}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quote").value(250));

        mvc.perform(post("/api/admin/bookings/" + id + "/approve").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(bookings.findById(id).orElseThrow().getQuote()).isEqualByComparingTo(BigDecimal.valueOf(250));

        // Approval emails the customer (studio + ack from creation = 2, plus this one = 3), BCC the studio inbox.
        var sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(3)).send(sent.capture());
        // Sends are async, so order isn't guaranteed — find messages by subject.
        var confirm = bySubject(sent, "confirmed");
        assertThat(addresses(confirm.getRecipients(MimeMessage.RecipientType.TO))).containsExactly("ava@example.com");
        assertThat(addresses(confirm.getRecipients(MimeMessage.RecipientType.BCC))).containsExactly("bookings@test.local");
        assertThat(confirm.getSubject()).contains("confirmed");
        var confirmBodies = bodies(confirm);
        assertThat(confirmBodies[0]).contains("$250.00").contains("Golden hour").contains("Hi Ava,");
        assertThat(confirmBodies[1]).contains("Session price").contains("$250.00");

        // The admin can preview the draft and send an edited version; cancelling a confirmed session emails too.
        mvc.perform(get("/api/admin/bookings/" + id + "/draft").param("action", "cancel").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(org.hamcrest.Matchers.containsString("cancelled")))
                .andExpect(jsonPath("$.to").value("ava@example.com"));
        mvc.perform(post("/api/admin/bookings/" + id + "/decline").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"So sorry, Ava\",\"body\":\"Hi Ava,\\n\\nWe have a conflict that day.\\n\\nErica\"}"))
                .andExpect(status().isOk());
        verify(mailSender, timeout(5000).times(4)).send(sent.capture());
        var cancel = bySubject(sent, "So sorry, Ava");
        assertThat(bodies(cancel)[1]).contains("We have a conflict that day.");

        // Restoring to pending is silent; approving with sendEmail=false is silent too.
        mvc.perform(post("/api/admin/bookings/" + id + "/restore").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/bookings/" + id + "/approve").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sendEmail\":false}"))
                .andExpect(status().isOk());
        Thread.sleep(300);
        verify(mailSender, timeout(1000).times(4)).send(any(MimeMessage.class));

        // Free-form message, no status change.
        mvc.perform(post("/api/admin/bookings/" + id + "/message").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Outfit ideas\",\"body\":\"Hi Ava,\\n\\nNeutral colours photograph best at golden hour.\"}"))
                .andExpect(status().isAccepted());
        verify(mailSender, timeout(5000).times(5)).send(sent.capture());
        assertThat(bySubject(sent, "Outfit ideas")).isNotNull();
        assertThat(bookings.findById(id).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // A confirmed session can get a gallery; a second one is refused.
        mvc.perform(post("/api/admin/galleries").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":" + id + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.stage").value(0));
        mvc.perform(post("/api/admin/galleries").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":" + id + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Erica\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/auth/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Erica"))
                .andExpect(jsonPath("$[0].initial").value("E"));
    }

    @Test
    void pricingIsSeededAndEditable() throws Exception {
        mvc.perform(get("/api/pricing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[2].key").value("weddings"))
                .andExpect(jsonPath("$[2].tiers.length()").value(3));

        var auth = "Bearer " + login("Austin", "test-pass");
        // Edit a category: rename, new "from", one package with an includes list.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/pricing")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"key\":\"weddings\",\"name\":\"Weddings & elopements\",\"shortName\":\"Weddings\",\"description\":\"Big or small.\",\"from\":\"$900\","
                                + "\"tiers\":[{\"name\":\"Intimate\",\"detail\":\"2 hours\",\"price\":\"$900\",\"includes\":[\"2 hours\",\"  \",\"Online gallery\"]}]}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='weddings')].name").value("Weddings & elopements"))
                .andExpect(jsonPath("$[?(@.key=='weddings')].from").value("$900"))
                .andExpect(jsonPath("$[?(@.key=='weddings')].tiers.length()").value(1))
                .andExpect(jsonPath("$[?(@.key=='weddings')].tiers[0].includes.length()").value(2)); // blank item dropped

        // Add a category: slug key, appended last; it gets portfolio photo slots.
        mvc.perform(post("/api/admin/pricing").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pets & Animals\",\"shortName\":\"Pets\",\"description\":\"Furry friends.\",\"from\":\"$120\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[6].key").value("pets-animals"))
                .andExpect(jsonPath("$[6].shortName").value("Pets"));
        mvc.perform(get("/api/admin/photos").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title=='Portfolio · Pets')].slots[0].id").value("pf-pets-animals-1"));

        // Remove it again, then reset everything to the launch list.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/pricing/pets-animals").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        mvc.perform(post("/api/admin/pricing/reset").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].key").value("weddings"))
                .andExpect(jsonPath("$[2].from").value("$800"))
                .andExpect(jsonPath("$[2].tiers.length()").value(3))
                .andExpect(jsonPath("$[2].tiers[1].includes.length()").value(5));
    }

    private String login(String name, String password) throws Exception {
        var res = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }
}
