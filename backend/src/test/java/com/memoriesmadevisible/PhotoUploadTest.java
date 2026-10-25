package com.memoriesmadevisible;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhotoUploadTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean JavaMailSender mailSender;
    @MockitoBean S3Client s3Client;

    @Test
    void uploadStoresInS3AndExposesPublicUrl() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        var res = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Erica", "password", "test-pass"))))
                .andReturn().getResponse().getContentAsString();
        var auth = "Bearer " + json.readTree(res).get("token").asText();

        var file = new MockMultipartFile("file", "hero.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/admin/photos/home-hero").file(file).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value("home-hero"))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("http://cdn.test/photos/site/home-hero/")));

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        mvc.perform(get("/api/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['home-hero']").exists());

        // Unknown slot and non-image content are both refused.
        mvc.perform(multipart("/api/admin/photos/not-a-slot").file(file).header("Authorization", auth))
                .andExpect(status().isBadRequest());
        var text = new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1});
        mvc.perform(multipart("/api/admin/photos/home-feat-1").file(text).header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }
}
