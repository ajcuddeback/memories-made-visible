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
import java.util.List;
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
                "packageName", "Intimate",
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
                .andExpect(jsonPath("$.packageName").value("Intimate"))
                .andExpect(jsonPath("$.startTime").value("14:00"))
                .andExpect(jsonPath("$.endTime").value("16:00")) // the Intimate package is 2 hours
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
                "sessionType", "families", "packageName", "Solo", "name", "Bot", "email", "bot@example.com",
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
                        "sessionType", "seniors", "packageName", "Mini", "name", "Ava", "email", "ava@example.com",
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
                .andExpect(jsonPath("$[?(@.name=='Erica')].initial").value("E"));
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
                .andExpect(jsonPath("$[2].tiers[1].includes.length()").value(5))
                .andExpect(jsonPath("$[5].tiers.length()").value(1))
                .andExpect(jsonPath("$[5].tiers[0].pricingModel").value("HOURLY"))
                .andExpect(jsonPath("$[5].tiers[0].minHours").value(2))
                .andExpect(jsonPath("$[5].tiers[0].addons[0].name").value("Second photographer"))
                .andExpect(jsonPath("$[5].tiers[0].addons[0].perHour").value(true));
    }

    private String login(String name, String password) throws Exception {
        var res = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    @Autowired com.memoriesmadevisible.publish.PublishedSiteRepository snapshots;

    @Test
    void oldPricingSnapshotsAreServedWithDefaults() throws Exception {
        // A snapshot written before add-ons/hourly packages existed: tiers with only name/detail/price/includes.
        var row = snapshots.findById("pricing").orElseThrow();
        String original = row.getPayload();
        try {
            row.setPayload("[{\"key\":\"legacy\",\"name\":\"Legacy\",\"description\":\"\",\"from\":\"$100\",\"showInPortfolio\":true,"
                    + "\"tiers\":[{\"name\":\"Old\",\"detail\":\"\",\"price\":\"$100\",\"includes\":[\"x\"]}]}]");
            snapshots.save(row);
            mvc.perform(get("/api/pricing"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].tiers[0].addons").isArray())
                    .andExpect(jsonPath("$[0].tiers[0].pricingModel").value("FLAT"))
                    .andExpect(jsonPath("$[0].tiers[0].durationMinutes").value(60))
                    .andExpect(jsonPath("$[0].tiers[0].minHours").value(2))
                    .andExpect(jsonPath("$[0].shortName").value("Legacy"));
        } finally {
            row.setPayload(original);
            snapshots.save(row);
        }
    }

    @Test
    void discardClearsUnpublishedChangesEvenAgainstAnOlderSnapshot() throws Exception {
        String auth = token("Austin", "test-pass");
        // Start clean.
        mvc.perform(post("/api/admin/publish").header("Authorization", auth)).andExpect(jsonPath("$.dirty").value(false));
        // Rewrite the published pricing in the shape an older version stored it: no lengths, models, hour ranges or add-ons.
        var row = snapshots.findById("pricing").orElseThrow();
        var stripped = json.readTree(row.getPayload());
        for (var type : stripped) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) type).remove("durationMinutes");
            for (var tier : type.get("tiers")) for (String f : List.of("durationMinutes", "pricingModel", "minHours", "maxHours", "addons"))
                ((com.fasterxml.jackson.databind.node.ObjectNode) tier).remove(f);
        }
        row.setPayload(stripped.toString());
        snapshots.save(row);
        // After an edit + Discard the banner must clear, not stay stuck until a Publish (the draft is rebuilt from
        // the old snapshot with today's defaults filled in, and the comparison fills them in the same way).
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/pricing")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"key\":\"couples\",\"name\":\"Couples\",\"description\":\"x\",\"from\":\"$1\",\"showInPortfolio\":true,\"tiers\":[]}]"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/publish").header("Authorization", auth)).andExpect(jsonPath("$.changes.pricing").value(true));
        mvc.perform(post("/api/admin/publish/discard").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changes.pricing").value(false)).andExpect(jsonPath("$.dirty").value(false));
        mvc.perform(get("/api/admin/pricing").header("Authorization", auth))
                .andExpect(jsonPath("$[?(@.key=='couples')].tiers.length()").value(2)); // the draft is back to what's live
        // Tidy: the old snapshot carried no package lengths, so put the launch list back and publish it for the other tests.
        mvc.perform(post("/api/admin/pricing/reset").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/publish").header("Authorization", auth)).andExpect(jsonPath("$.changes.pricing").value(false));
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

        // Default hours 07:00–18:00 → the 1-hour "Family" package can start every 30 min from 7:00 to 17:00 (21 slots).
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Family"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value(d))
                .andExpect(jsonPath("$[0].slots.length()").value(21))
                .andExpect(jsonPath("$[0].slots[0].start").value("07:00"))
                .andExpect(jsonPath("$[0].slots[0].label").value("7:00 AM"))
                .andExpect(jsonPath("$[0].slots[-1].start").value("17:00"));
        // The 30-minute "Solo" package gets one more start (17:30); the 8-hour "Full day" wedding only 7:00 … 10:00 (7).
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Solo"))
                .andExpect(jsonPath("$[0].slots.length()").value(22));
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "weddings").param("package", "Full day"))
                .andExpect(jsonPath("$[0].slots.length()").value(7));
        // A package's length is published like a price: edit "Full day" 8h → 10h in the draft, Publish, and new bookings block 10 hours.
        var wedding = json.readTree(mvc.perform(get("/api/admin/pricing").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString());
        com.fasterxml.jackson.databind.node.ObjectNode wNode = null;
        for (var n : wedding) if (n.get("key").asText().equals("weddings")) wNode = (com.fasterxml.jackson.databind.node.ObjectNode) n;
        for (var tier : wNode.get("tiers")) if (tier.get("name").asText().equals("Full day")) ((com.fasterxml.jackson.databind.node.ObjectNode) tier).put("durationMinutes", 600);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/pricing")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("[" + wNode + "]"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/publish").header("Authorization", auth))
                .andExpect(jsonPath("$.changes.pricing").value(true));
        mvc.perform(post("/api/admin/publish").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/pricing"))
                .andExpect(jsonPath("$[?(@.key=='weddings')].tiers[?(@.name=='Full day')].durationMinutes").value(600));
        LocalDate wDay = day.plusDays(1);
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sessionType", "weddings", "packageName", "Full day", "name", "Kai Moss", "email", "kai@example.com", "date", wDay.toString(), "time", "07:00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("07:00"))
                .andExpect(jsonPath("$.endTime").value("17:00"));
        // …and the whole day is gone for everyone else.
        mvc.perform(get("/api/availability").param("from", wDay.toString()).param("to", wDay.toString()).param("type", "families").param("package", "Solo"))
                .andExpect(jsonPath("$[0].slots[*].start").value(org.hamcrest.Matchers.contains("17:00", "17:30"))); // only after the wedding
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sessionType", "families", "packageName", "Solo", "name", "Jo", "email", "jo@example.com", "date", wDay.toString(), "time", "12:00"))))
                .andExpect(status().isConflict());

        // Hand-crafted requests can't do more than the form: off-grid start, in the past, too soon, too far out.
        var base = new java.util.HashMap<String, Object>(Map.of("sessionType", "families", "packageName", "Solo", "name", "Edge", "email", "edge@example.com"));
        base.put("date", d); base.put("time", "09:15");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(base)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("half hour")));
        base.put("date", LocalDate.now().minusDays(1).toString()); base.put("time", "10:00");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(base)))
                .andExpect(status().isConflict());
        base.put("date", LocalDate.now().plusYears(3).toString());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(base)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("18 months")));
        base.put("date", d); base.put("time", "9:00 AM");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(base)))
                .andExpect(status().isBadRequest());
        base.put("time", "17:30"); base.put("packageName", "Family"); // 1 hour from 17:30 ends past 18:00
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(base)))
                .andExpect(status().isConflict());
        // A category with packages needs one; a missing package is a 400, not a silent 1-hour booking.
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sessionType", "families", "name", "X", "email", "x@example.com", "date", d, "time", "08:00"))))
                .andExpect(status().isBadRequest());
        // Two packages with the same name in one category are refused (bookings reference packages by name).
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/pricing")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"key\":\"couples\",\"name\":\"Couples\",\"description\":\"\",\"from\":\"$150\",\"showInPortfolio\":true,"
                                + "\"tiers\":[{\"name\":\"Mini\",\"detail\":\"\",\"price\":\"$150\",\"includes\":[]},{\"name\":\"mini\",\"detail\":\"\",\"price\":\"$200\",\"includes\":[]}]}]"))
                .andExpect(status().isBadRequest());

        // Hourly package: Event coverage is $150/hr, 2–8 hours, with a per-hour add-on. 3 hours + second photographer
        // blocks 3 hours and quotes 3×150 + 3×75 = 675. Hours outside the range, or a made-up add-on, are refused.
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "events").param("package", "Event coverage").param("hours", "3"))
                .andExpect(jsonPath("$[0].slots[-1].start").value("15:00")); // last 3-hour start that ends by 18:00
        var ev = new java.util.HashMap<String, Object>(Map.of("sessionType", "events", "packageName", "Event coverage", "hours", 3,
                "addons", List.of("Second photographer"), "name", "Party Host", "email", "host@example.com", "time", "14:00"));
        ev.put("date", day.plusDays(2).toString());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ev)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hours").value(3))
                .andExpect(jsonPath("$.addons[0]").value("Second photographer"))
                .andExpect(jsonPath("$.needsSecond").value(true))
                .andExpect(jsonPath("$.lead").value("Erica"))
                .andExpect(jsonPath("$.startTime").value("14:00"))
                .andExpect(jsonPath("$.endTime").value("17:00"))
                .andExpect(jsonPath("$.quote").value(675));
        ev.put("hours", 1);
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ev)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2 to 8 hours")));
        ev.put("hours", 2); ev.put("addons", List.of("Drone"));
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ev)))
                .andExpect(status().isBadRequest());
        ev.remove("hours"); ev.put("addons", List.of());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ev)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("how many hours")));

        // A package that isn't on the price list is refused.
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sessionType", "families", "packageName", "Deluxe", "name", "X", "email", "x@example.com", "date", d, "time", "09:00"))))
                .andExpect(status().isBadRequest());

        // Block 12:00–14:00 (a private note the public never sees) → the 10:30–13:30 starts disappear for a 1-hour session.
        mvc.perform(post("/api/admin/availability/blocks").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + d + "\",\"startTime\":\"12:00\",\"endTime\":\"14:00\",\"note\":\"Dentist\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.note").value("Dentist"));
        var slots = json.readTree(mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Family"))
                .andReturn().getResponse().getContentAsString()).get(0).get("slots");
        var starts = new java.util.ArrayList<String>();
        slots.forEach(sl -> starts.add(sl.get("start").asText()));
        assertThat(starts).contains("11:00", "14:00").doesNotContain("11:30", "12:00", "13:30");
        assertThat(slots.toString()).doesNotContain("Dentist");

        // Book 09:00; the slot (and the overlapping 08:30/09:30 starts) go away while the request is pending.
        var body = Map.of("sessionType", "families", "packageName", "Family", "name", "Noah Bell", "email", "noah@example.com", "date", d, "time", "09:00");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.time").value("9:00 AM"))
                .andExpect(jsonPath("$.startTime").value("09:00"))
                .andExpect(jsonPath("$.endTime").value("10:00"));
        slots = json.readTree(mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Family"))
                .andReturn().getResponse().getContentAsString()).get(0).get("slots");
        starts.clear();
        slots.forEach(sl -> starts.add(sl.get("start").asText()));
        assertThat(starts).contains("08:00", "10:00").doesNotContain("08:30", "09:00", "09:30");

        // A second request for the same time is refused (409), as is one outside hours or in the block.
        var again = Map.of("sessionType", "couples", "packageName", "Mini", "name", "Liv Park", "email", "liv@example.com", "date", d, "time", "09:30");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(again)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sessionType", "couples", "packageName", "Mini", "name", "Liv Park", "email", "liv@example.com", "date", d, "time", "19:00"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sessionType", "couples", "packageName", "Mini", "name", "Liv Park", "email", "liv@example.com", "date", d, "time", "12:30"))))
                .andExpect(status().isConflict());

        // Closing that weekday removes every slot; reopening 09:00–12:00 narrows them.
        int wd = day.getDayOfWeek().getValue() % 7;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/availability/hours")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"weekday\":" + wd + ",\"open\":false,\"start\":\"07:00\",\"end\":\"18:00\"}]"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Family"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/availability/hours")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"weekday\":" + wd + ",\"open\":true,\"start\":\"09:00\",\"end\":\"12:00\"}]"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Family"))
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

    @Test
    void analyticsAreRecordedAnonymouslyAndReported() throws Exception {
        var res = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Erica\",\"password\":\"test-pass\"}"))
                .andReturn().getResponse().getContentAsString();
        var auth = "Bearer " + json.readTree(res).get("token").asText();

        // Two visitors; one walks the booking funnel to the end, the other bounces. Admin pages and bad kinds are ignored.
        String batch = """
            {"events":[
              {"visitor":"v1","session":"s1","kind":"view","path":"/","referrer":"https://www.instagram.com/p/abc","device":"mobile"},
              {"visitor":"v1","session":"s1","kind":"leave","path":"/","device":"mobile","durationMs":12000},
              {"visitor":"v1","session":"s1","kind":"view","path":"/book?type=weddings","device":"mobile"},
              {"visitor":"v1","session":"s1","kind":"book_view","path":"/book","device":"mobile"},
              {"visitor":"v1","session":"s1","kind":"book_type","path":"/book","device":"mobile"},
              {"visitor":"v1","session":"s1","kind":"book_slot","path":"/book","device":"mobile"},
              {"visitor":"v1","session":"s1","kind":"book_details","path":"/book","device":"mobile"},
              {"visitor":"v1","session":"s1","kind":"book_submit","path":"/book","device":"mobile"},
              {"visitor":"v1","session":"s1","kind":"leave","path":"/book","device":"mobile","durationMs":48000},
              {"visitor":"v2","session":"s2","kind":"view","path":"/pricing","device":"desktop"},
              {"visitor":"v2","session":"s2","kind":"leave","path":"/pricing","device":"desktop","durationMs":5000},
              {"visitor":"v3","session":"s3","kind":"view","path":"/studio/sessions","device":"desktop"},
              {"visitor":"v3","session":"s3","kind":"hack","path":"/","device":"desktop"}
            ]}""";
        mvc.perform(post("/api/analytics").contentType(MediaType.APPLICATION_JSON).content(batch)).andExpect(status().isNoContent());
        // sendBeacon can only send text/plain; a page-leave beacon must land too (counted once more for v2's leave).
        mvc.perform(post("/api/analytics").contentType(MediaType.TEXT_PLAIN)
                        .content("{\"events\":[{\"visitor\":\"v2\",\"session\":\"s2\",\"kind\":\"leave\",\"path\":\"/pricing\",\"device\":\"desktop\",\"durationMs\":1000}]}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/analytics").contentType(MediaType.TEXT_PLAIN).content("not json")).andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/analytics").param("days", "7").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.visitors").value(2))
                .andExpect(jsonPath("$.totals.visits").value(2))
                .andExpect(jsonPath("$.totals.pageviews").value(3))
                .andExpect(jsonPath("$.totals.avgVisitSeconds").value(33))   // (60s + 6s) / 2, rounded
                .andExpect(jsonPath("$.totals.bounceRate").value(0.5))
                .andExpect(jsonPath("$.pages[?(@.label=='/book')].count").value(1)) // query string dropped
                .andExpect(jsonPath("$.referrers[?(@.label=='instagram.com')].count").value(1))
                .andExpect(jsonPath("$.referrers[?(@.label=='Direct / typed in')].count").value(1))
                .andExpect(jsonPath("$.devices[?(@.label=='mobile')].count").value(1))
                .andExpect(jsonPath("$.funnel[0].visits").value(1))
                .andExpect(jsonPath("$.funnel[4].visits").value(1))
                .andExpect(jsonPath("$.bookingsSubmitted").value(1))
                .andExpect(jsonPath("$.daily.length()").value(7));
        // Public readers can't see the report.
        mvc.perform(get("/api/admin/analytics")).andExpect(status().isForbidden());
    }

    @Test
    void teamAccountsRolesInvitesAndPasswordReset() throws Exception {
        var sent = ArgumentCaptor.forClass(MimeMessage.class);
        String owner = token("Austin", "test-pass");
        String admin = token("Erica", "test-pass");

        // Seeded: Austin is the owner, Erica an admin.
        mvc.perform(get("/api/admin/auth/me").header("Authorization", owner))
                .andExpect(jsonPath("$.access").value("OWNER")).andExpect(jsonPath("$.mustChangePassword").value(false));
        mvc.perform(get("/api/admin/auth/me").header("Authorization", admin)).andExpect(jsonPath("$.access").value("ADMIN"));

        // An admin adds an employee → invite email with a temporary password.
        int before = org.mockito.Mockito.mockingDetails(mailSender).getInvocations().size();
        mvc.perform(post("/api/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sam\",\"role\":\"Assistant\",\"email\":\"sam@test.local\",\"access\":\"EMPLOYEE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access").value("EMPLOYEE"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        Thread.sleep(400);
        verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(sent.capture());
        var invite = bySubject(sent, "studio team");
        assertThat(addresses(invite.getRecipients(MimeMessage.RecipientType.TO))).containsExactly("sam@test.local");
        String text = bodies(invite)[0];
        var m = java.util.regex.Pattern.compile("Temporary password: (\\S+)").matcher(text);
        assertThat(m.find()).isTrue();
        String temp = m.group(1);
        assertThat(text).contains("employee");

        // Nobody can hand out OWNER; an admin can't touch the owner; a duplicate name is refused.
        mvc.perform(post("/api/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Boss\",\"role\":\"x\",\"email\":\"boss@test.local\",\"access\":\"OWNER\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/users/Austin").header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sam\",\"role\":\"x\",\"email\":\"other@test.local\",\"access\":\"EMPLOYEE\"}"))
                .andExpect(status().isBadRequest());

        // Sam signs in with the temporary password, is flagged to change it, and can't reach admin-only things.
        var samLogin = json.readTree(mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Sam", "password", temp))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString());
        String samTemp = "Bearer " + samLogin.get("token").asText();
        // Until the temporary password is replaced, nothing but "me" and "change password" is open.
        mvc.perform(get("/api/admin/auth/me").header("Authorization", samTemp)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/bookings").header("Authorization", samTemp)).andExpect(status().isForbidden());

        // Sam sets a real password; the temporary one stops working and a fresh token comes back.
        var samChanged = json.readTree(mvc.perform(post("/api/admin/auth/password").header("Authorization", samTemp).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("current", temp, "next", "sams-new-password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString());
        String sam = "Bearer " + samChanged.get("token").asText();
        mvc.perform(get("/api/admin/bookings").header("Authorization", sam)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/availability/blocks").header("Authorization", sam)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/pricing").header("Authorization", sam)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/analytics").header("Authorization", sam)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", sam)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/publish").header("Authorization", sam)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Sam", "password", temp))))
                .andExpect(status().isUnauthorized());

        // Forgot password: a reset link by email, then a new password through it. Unknown emails get the same 204.
        mvc.perform(post("/api/admin/auth/forgot").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@test.local\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/admin/auth/forgot").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"SAM@test.local\"}"))
                .andExpect(status().isNoContent());
        Thread.sleep(400);
        verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(sent.capture());
        var resetMail = bySubject(sent, "Reset your studio password");
        var tm = java.util.regex.Pattern.compile("reset=([0-9a-f]+)").matcher(bodies(resetMail)[0]);
        assertThat(tm.find()).isTrue();
        mvc.perform(post("/api/admin/auth/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", tm.group(1), "password", "reset-password-1"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.name").value("Sam"));
        mvc.perform(post("/api/admin/auth/reset").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", tm.group(1), "password", "again"))))
                .andExpect(status().isBadRequest()); // single use

        // Assignments: Sam (employee) sees nothing until an admin puts them on a session; then only that one.
        // Reactivate Sam first for this part.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/Sam").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        var samLogin2 = json.readTree(mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Sam", "password", "reset-password-1"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String sam2 = "Bearer " + samLogin2.get("token").asText();
        for (String who : List.of("Assign One", "Assign Two")) {
            var b = new Booking();
            b.setSessionTypeKey("families"); b.setName(who); b.setEmail(who.replace(" ", "").toLowerCase() + "@example.com");
            b.setDate(LocalDate.now().plusDays(80)); b.setTime("9:00 AM"); b.setStatus(BookingStatus.CONFIRMED);
            bookings.save(b);
        }
        var all = json.readTree(mvc.perform(get("/api/admin/bookings").header("Authorization", owner)).andReturn().getResponse().getContentAsString());
        assertThat(all.size()).isGreaterThan(1);
        long someId = -1, otherId = -1;
        for (var n : all) { if (n.get("name").asText().equals("Assign One")) someId = n.get("id").asLong(); if (n.get("name").asText().equals("Assign Two")) otherId = n.get("id").asLong(); }
        mvc.perform(get("/api/admin/bookings").header("Authorization", sam2)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/bookings/" + someId + "/assignees")
                        .header("Authorization", sam2).contentType(MediaType.APPLICATION_JSON).content("{\"lead\":\"Sam\"}"))
                .andExpect(status().isForbidden()); // employees can't assign themselves
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/bookings/" + someId + "/assignees")
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"lead\":\"Sam\",\"second\":\"Nobody\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/bookings/" + someId + "/assignees")
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"lead\":\"Sam\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lead").value("Sam")).andExpect(jsonPath("$.assignees[0]").value("Sam"));
        mvc.perform(get("/api/admin/bookings").header("Authorization", sam2))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(someId));
        Thread.sleep(400);
        verify(mailSender, org.mockito.Mockito.atLeastOnce()).send(sent.capture());
        assertThat(bySubject(sent, "You're on ")).isNotNull();
        // As lead, Sam may run the session (quote, approve with no email); as second, Sam may not.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/bookings/" + someId + "/quote")
                        .header("Authorization", sam2).contentType(MediaType.APPLICATION_JSON).content("{\"quote\":180}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quote").value(180));
        mvc.perform(post("/api/admin/bookings/" + someId + "/approve").header("Authorization", sam2)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sendEmail\":false}"))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/bookings/" + otherId + "/assignees")
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"lead\":\"Erica\",\"second\":\"Sam\"}"))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/bookings/" + otherId + "/quote")
                        .header("Authorization", sam2).contentType(MediaType.APPLICATION_JSON).content("{\"quote\":1}"))
                .andExpect(status().isForbidden());
        // An employee may edit their own week but nobody else's.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/availability/week").param("staff", "Sam")
                        .header("Authorization", sam2).contentType(MediaType.APPLICATION_JSON).content("[{\"weekday\":1,\"open\":false,\"start\":\"07:00\",\"end\":\"18:00\"}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.custom").value(true));
        mvc.perform(get("/api/admin/availability/week").param("staff", "Erica").header("Authorization", sam2)).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/bookings/" + otherId + "/assignees")
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        // Sam can't start a gallery on a session they're not on.
        mvc.perform(post("/api/admin/galleries").header("Authorization", sam2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + otherId + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/bookings/" + someId + "/assignees")
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignees.length()").value(0));

        // Deactivated accounts can't sign in or use an old token; the owner can promote and remove.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/Sam").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/admin/bookings").header("Authorization", sam)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auth/users")).andExpect(jsonPath("$[?(@.name=='Sam')]").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/Sam").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true,\"access\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.access").value("ADMIN"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/users/Sam").header("Authorization", owner))
                .andExpect(status().isNoContent());
    }

    private String token(String name, String password) throws Exception {
        var res = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(res).get("token").asText();
    }

    @Test
    void customersCanPickAPhotographerAndCapacityIsPerPerson() throws Exception {
        String owner = token("Austin", "test-pass");
        // Nobody bookable → the public list is empty and the studio books as one unit.
        mvc.perform(get("/api/photographers")).andExpect(jsonPath("$.length()").value(0));

        // Mark Erica and Austin bookable.
        for (String n : List.of("Erica", "Austin")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/" + n).header("Authorization", owner)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"bookable\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.bookable").value(true));
        }
        mvc.perform(get("/api/photographers")).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].name").value("Austin"));

        LocalDate day = LocalDate.now().plusDays(120);
        while (day.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || day.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) day = day.plusDays(1);
        String d = day.toString();

        // Erica blocks 09:00–12:00 for herself only: her 09:00 is gone, Austin's isn't, and "no preference" still has it.
        mvc.perform(post("/api/admin/availability/blocks").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + d + "\",\"startTime\":\"09:00\",\"endTime\":\"12:00\",\"note\":\"Dentist\",\"staffName\":\"Erica\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.staffName").value("Erica"));
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Family").param("photographer", "Erica"))
                .andExpect(jsonPath("$[0].slots[?(@.start=='09:00')]").isEmpty())
                .andExpect(jsonPath("$[0].slots[?(@.start=='13:00')]").isNotEmpty());
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Family").param("photographer", "Austin"))
                .andExpect(jsonPath("$[0].slots[?(@.start=='09:00')]").isNotEmpty());
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Family"))
                .andExpect(jsonPath("$[0].slots[?(@.start=='09:00')]").isNotEmpty());
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Family").param("photographer", "Sam"))
                .andExpect(status().isBadRequest());

        // Booking Austin at 14:00 assigns him; a second request for Austin at 14:00 is refused, but Erica is still free,
        // and "no preference" lands on Erica (the free one).
        var req = new java.util.HashMap<String, Object>(Map.of("sessionType", "families", "packageName", "Family", "name", "Pat", "email", "pat@example.com", "date", d, "time", "14:00"));
        req.put("photographer", "Austin");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.assignees[0]").value("Austin"));
        req.put("email", "pat2@example.com");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isConflict());
        req.remove("photographer"); req.put("email", "pat3@example.com");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.assignees[0]").value("Erica"));
        // Now both are taken at 14:00: nobody is free.
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Family"))
                .andExpect(jsonPath("$[0].slots[?(@.start=='14:00')]").isEmpty());
        req.put("email", "pat4@example.com");
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isConflict());

        // Handing a session off checks the new person's calendar: Pat (14:00, Austin) can't go to Erica, who has Pat3 at 14:00.
        var patId = bookings.findAll().stream().filter(b -> b.getEmail().equals("pat@example.com")).findFirst().orElseThrow().getId();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/bookings/" + patId + "/assignees")
                        .header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content("{\"lead\":\"Erica\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Erica isn't free then")));

        // A studio-wide block (no staffName) stops everyone.
        mvc.perform(post("/api/admin/availability/blocks").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + d + "\",\"startTime\":\"16:00\",\"endTime\":\"17:00\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Solo").param("photographer", "Austin"))
                .andExpect(jsonPath("$[0].slots[?(@.start=='16:00')]").isEmpty());

        // Per-person hours: Erica only works 13:00–18:00 that weekday → her 12:30 start is gone, Austin's isn't,
        // and a hand-crafted request for Erica at 12:30 is refused. Clearing her week restores the studio's.
        int wd = day.getDayOfWeek().getValue() % 7;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/availability/week").param("staff", "Erica")
                        .header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"weekday\":" + wd + ",\"open\":true,\"start\":\"13:00\",\"end\":\"18:00\"}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.custom").value(true)).andExpect(jsonPath("$.staffName").value("Erica"));
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Solo").param("photographer", "Erica"))
                .andExpect(jsonPath("$[0].slots[?(@.start=='12:30')]").isEmpty())
                .andExpect(jsonPath("$[0].slots[?(@.start=='13:00')]").isNotEmpty());
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Solo").param("photographer", "Austin"))
                .andExpect(jsonPath("$[0].slots[?(@.start=='12:30')]").isNotEmpty());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sessionType", "families", "packageName", "Solo", "name", "Early Bird", "email", "early@example.com", "date", d, "time", "12:30", "photographer", "Erica"))))
                .andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/availability/week").param("staff", "Erica").header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.custom").value(false));
        mvc.perform(get("/api/availability").param("from", d).param("to", d).param("type", "families").param("package", "Solo").param("photographer", "Erica"))
                .andExpect(jsonPath("$[0].slots[?(@.start=='12:30')]").isNotEmpty());

        // Tidy: unflag both so the other tests see a single-capacity studio again.
        for (String n : List.of("Erica", "Austin")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/users/" + n).header("Authorization", owner)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"bookable\":false}"))
                    .andExpect(status().isOk());
        }
    }
}
