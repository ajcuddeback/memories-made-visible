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
import org.springframework.mail.SimpleMailMessage;
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
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000).times(2)).send(captor.capture());
        var studio = captor.getAllValues().get(0);
        assertThat(studio.getTo()).containsExactly("bookings@test.local");
        assertThat(studio.getFrom()).isEqualTo("admin@test.local");
        assertThat(studio.getReplyTo()).isEqualTo("taylor@example.com");
        assertThat(studio.getSubject()).contains("Weddings").contains("Taylor Reyes");
        assertThat(studio.getText()).contains("Winter Haven").contains("About 120 guests.").contains("/studio");
        var customer = captor.getAllValues().get(1);
        assertThat(customer.getTo()).containsExactly("taylor@example.com");
        assertThat(customer.getFrom()).isEqualTo("admin@test.local");
        assertThat(customer.getReplyTo()).isEqualTo("bookings@test.local");
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
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000).times(3)).send(sent.capture());
        var confirm = sent.getAllValues().get(2);
        assertThat(confirm.getTo()).containsExactly("ava@example.com");
        assertThat(confirm.getBcc()).containsExactly("bookings@test.local");
        assertThat(confirm.getSubject()).contains("confirmed");
        assertThat(confirm.getText()).contains("$250.00").contains("Golden hour");

        // Cancelling a confirmed session emails too; restoring to pending does not.
        mvc.perform(post("/api/admin/bookings/" + id + "/decline").header("Authorization", auth)).andExpect(status().isOk());
        verify(mailSender, timeout(5000).times(4)).send(sent.capture());
        assertThat(sent.getAllValues().get(sent.getAllValues().size() - 1).getSubject()).contains("cancelled");
        mvc.perform(post("/api/admin/bookings/" + id + "/restore").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/bookings/" + id + "/approve").header("Authorization", auth)).andExpect(status().isOk());
        verify(mailSender, timeout(5000).times(5)).send(any(SimpleMailMessage.class));

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
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/pricing")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"key\":\"weddings\",\"from\":\"$900\",\"tiers\":[{\"name\":\"Intimate\",\"detail\":\"2 hours\",\"price\":\"$900\"}]}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].from").value("$900"))
                .andExpect(jsonPath("$[2].tiers.length()").value(1));

        mvc.perform(post("/api/admin/pricing/reset").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].from").value("$800"))
                .andExpect(jsonPath("$[2].tiers.length()").value(3));
    }

    private String login(String name, String password) throws Exception {
        var res = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }
}
