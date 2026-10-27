package com.memoriesmadevisible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoriesmadevisible.booking.Booking;
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
    @Autowired com.memoriesmadevisible.gallery.GalleryRepository galleries;

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
                "time", "14:00");

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
                "date", LocalDate.now().plusDays(3).toString(), "time", "09:00", "website", "http://spam");
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
                        "date", LocalDate.now().plusDays(10).toString(), "time", "16:30"))))
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
        assertThat(confirmBodies[0]).contains("$250.00").contains("4:30 PM").contains("Hi Ava,");
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

        // Deliver it: the drafted email, then link + final price → stage 3, quote updated, client emailed.
        var galleryId = galleries.findAll().stream().filter(g -> g.getBookingId().equals(id)).findFirst().orElseThrow().getId();
        mvc.perform(get("/api/admin/galleries/" + galleryId + "/draft").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(org.hamcrest.Matchers.endsWith("photos are ready!")))
                .andExpect(jsonPath("$.to").value("ava@example.com"));
        mvc.perform(post("/api/admin/galleries/" + galleryId + "/deliver").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"link\":\"not a url\",\"finalPrice\":1250}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/galleries/" + galleryId + "/deliver").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"link\":\"https://photos.example.com/ava\",\"finalPrice\":1250,\"subject\":\"Ava, your photos are here\",\"body\":\"Hi Ava,\\n\\nEnjoy!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gallery.stage").value(3))
                .andExpect(jsonPath("$.gallery.link").value("https://photos.example.com/ava"))
                .andExpect(jsonPath("$.gallery.deliveredAt").exists())
                .andExpect(jsonPath("$.booking.quote").value(1250));
        verify(mailSender, timeout(5000).times(6)).send(sent.capture());
        var delivered = bodies(bySubject(sent, "Ava, your photos are here"));
        assertThat(delivered[1]).contains("https://photos.example.com/ava").contains("View your photos");
        assertThat(delivered[0]).contains("https://photos.example.com/ava");
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
                        .content("[{\"key\":\"weddings\",\"name\":\"Weddings & elopements\",\"shortName\":\"Weddings\",\"description\":\"Big or small.\",\"from\":\"$900\",\"showInPortfolio\":false,"
                                + "\"tiers\":[{\"name\":\"Intimate\",\"detail\":\"2 hours\",\"price\":\"$900\",\"includes\":[\"2 hours\",\"  \",\"Online gallery\"]}]}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='weddings')].name").value("Weddings & elopements"))
                .andExpect(jsonPath("$[?(@.key=='weddings')].from").value("$900"))
                .andExpect(jsonPath("$[?(@.key=='weddings')].tiers.length()").value(1))
                .andExpect(jsonPath("$[?(@.key=='weddings')].showInPortfolio").value(false))
                .andExpect(jsonPath("$[?(@.key=='weddings')].tiers[0].includes.length()").value(2)); // blank item dropped
        // Hidden from the portfolio → no photo slot group for it.
        mvc.perform(get("/api/admin/photos").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title=='Portfolio · Weddings')]").isEmpty());

        // Admin edits are a draft: the public list still shows the published three wedding packages.
        mvc.perform(get("/api/pricing"))
                .andExpect(jsonPath("$[?(@.key=='weddings')].tiers.length()").value(3));
        mvc.perform(get("/api/admin/publish").header("Authorization", auth))
                .andExpect(jsonPath("$.dirty").value(true))
                .andExpect(jsonPath("$.changes.pricing").value(true));
        // Discard puts the draft back to what's live…
        mvc.perform(post("/api/admin/publish/discard").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dirty").value(false));
        mvc.perform(get("/api/admin/pricing").header("Authorization", auth))
                .andExpect(jsonPath("$[?(@.key=='weddings')].tiers.length()").value(3))
                .andExpect(jsonPath("$[?(@.key=='weddings')].showInPortfolio").value(true));
        // …and Publish pushes a draft live.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/pricing")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"key\":\"weddings\",\"name\":\"Weddings\",\"description\":\"Big or small.\",\"from\":\"$950\",\"showInPortfolio\":true,"
                                + "\"tiers\":[{\"name\":\"Intimate\",\"detail\":\"2 hours\",\"price\":\"$950\",\"includes\":[]}]}]"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/publish").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dirty").value(false))
                .andExpect(jsonPath("$.publishedAt").exists());
        mvc.perform(get("/api/pricing"))
                .andExpect(jsonPath("$[?(@.key=='weddings')].from").value("$950"))
                .andExpect(jsonPath("$[?(@.key=='weddings')].tiers.length()").value(1));

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

    @Test
    void availabilityDrivesSlotsAndBlocksDoubleBooking() throws Exception {
        var res = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Austin\",\"password\":\"test-pass\"}"))
                .andReturn().getResponse().getContentAsString();
        var auth = "Bearer " + json.readTree(res).get("token").asText();

        // Pick a weekday well ahead so "today" rules don't interfere.
        LocalDate day = LocalDate.now().plusDays(45);
        while (day.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || day.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) day = day.plusDays(1);
        String d = day.toString();

        // Default hours 07:00–18:00 → a 60-minute family session can start every 30 min from 7:00 to 17:00 (21 slots).
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value(d))
                .andExpect(jsonPath("$[0].slots.length()").value(21))
                .andExpect(jsonPath("$[0].slots[0].start").value("07:00"))
                .andExpect(jsonPath("$[0].slots[0].label").value("7:00 AM"))
                .andExpect(jsonPath("$[0].slots[-1].start").value("17:00"));
        // A 4-hour wedding has fewer starts (7:00 … 14:00 = 15).
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "weddings"))
                .andExpect(jsonPath("$[0].slots.length()").value(15));

        // Block 12:00–14:00 (a private note the public never sees) → the 10:30–13:30 starts disappear for a 1-hour session.
        mvc.perform(post("/api/admin/availability/blocks").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + d + "\",\"startTime\":\"12:00\",\"endTime\":\"14:00\",\"note\":\"Dentist\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.note").value("Dentist"));
        var slots = json.readTree(mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families"))
                .andReturn().getResponse().getContentAsString()).get(0).get("slots");
        var starts = new java.util.ArrayList<String>();
        slots.forEach(sl -> starts.add(sl.get("start").asText()));
        assertThat(starts).contains("11:00", "14:00").doesNotContain("11:30", "12:00", "13:30");
        assertThat(slots.toString()).doesNotContain("Dentist");

        // Book 09:00; the slot (and the overlapping 08:30/09:30 starts) go away while the request is pending.
        var body = Map.of("sessionType", "families", "name", "Noah Bell", "email", "noah@example.com", "date", d, "time", "09:00");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.time").value("9:00 AM"))
                .andExpect(jsonPath("$.startTime").value("09:00"))
                .andExpect(jsonPath("$.endTime").value("10:00"));
        slots = json.readTree(mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families"))
                .andReturn().getResponse().getContentAsString()).get(0).get("slots");
        starts.clear();
        slots.forEach(sl -> starts.add(sl.get("start").asText()));
        assertThat(starts).contains("08:00", "10:00").doesNotContain("08:30", "09:00", "09:30");

        // A second request for the same time is refused (409), as is one outside hours or in the block.
        var again = Map.of("sessionType", "couples", "name", "Liv Park", "email", "liv@example.com", "date", d, "time", "09:30");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(again)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sessionType", "couples", "name", "Liv Park", "email", "liv@example.com", "date", d, "time", "19:00"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sessionType", "couples", "name", "Liv Park", "email", "liv@example.com", "date", d, "time", "12:30"))))
                .andExpect(status().isConflict());

        // Closing that weekday removes every slot; reopening 09:00–12:00 narrows them.
        int wd = day.getDayOfWeek().getValue() % 7;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/availability/hours")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"weekday\":" + wd + ",\"open\":false,\"start\":\"07:00\",\"end\":\"18:00\"}]"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/availability/hours")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"weekday\":" + wd + ",\"open\":true,\"start\":\"09:00\",\"end\":\"12:00\"}]"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families"))
                .andExpect(jsonPath("$[0].slots[0].start").value("10:00"))   // 09:00 is Noah's
                .andExpect(jsonPath("$[0].slots[-1].start").value("11:00"));

        // Admin: moving Noah to 10:30 frees 09:00; approving then claims 10:30 for real.
        var noahId = bookings.findAll().stream().filter(b -> b.getName().equals("Noah Bell")).findFirst().orElseThrow().getId();
        mvc.perform(get("/api/admin/bookings/" + noahId + "/reschedule-draft").header("Authorization", auth)
                        .param("reason", "WEATHER").param("date", d).param("time", "10:30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("New time for your Families & solo portraits session — please confirm"))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("forecast"), org.hamcrest.Matchers.containsString("9:00 AM"),
                        org.hamcrest.Matchers.containsString("10:30 AM"), org.hamcrest.Matchers.containsString("reply to this email to confirm"))));
        var moveSent = ArgumentCaptor.forClass(MimeMessage.class);
        int before = org.mockito.Mockito.mockingDetails(mailSender).getInvocations().size();
        mvc.perform(post("/api/admin/bookings/" + noahId + "/reschedule").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + d + "\",\"time\":\"10:30\",\"reason\":\"CLIENT_REQUEST\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.time").value("10:30 AM"));
        Thread.sleep(500);
        verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(moveSent.capture());
        var moveMail = bySubject(moveSent, "please confirm");
        assertThat(addresses(moveMail.getRecipients(MimeMessage.RecipientType.TO))).containsExactly("noah@example.com");
        assertThat(bodies(moveMail)[0]).contains("As you asked").contains("10:30 AM");
        mvc.perform(post("/api/admin/bookings/" + noahId + "/approve").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sendEmail\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        // Approving another request that overlaps a confirmed session is refused with a reason.
        var other = new Booking();
        other.setSessionTypeKey("couples"); other.setName("Zoe Hart"); other.setEmail("zoe@example.com");
        other.setDate(day); other.setStartTime(java.time.LocalTime.of(11, 0)); other.setEndTime(java.time.LocalTime.of(12, 0));
        other.setTime("11:00 AM"); other.setStatus(BookingStatus.PENDING);
        var otherId = bookings.save(other).getId();
        mvc.perform(post("/api/admin/bookings/" + otherId + "/approve").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sendEmail\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Noah Bell's confirmed session")));

        // Clean up the block and reopen the weekday for the other tests.
        var blockId = json.readTree(mvc.perform(get("/api/admin/availability/blocks").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/availability/blocks/" + blockId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/availability/hours")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"weekday\":" + wd + ",\"open\":true,\"start\":\"07:00\",\"end\":\"18:00\"}]"))
                .andExpect(status().isOk());
    }
}
