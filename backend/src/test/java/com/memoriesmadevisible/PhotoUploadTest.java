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

        var file = new MockMultipartFile("file", "hero.png", "image/png", pngOfSize(2400, 1600));
        mvc.perform(multipart("/api/admin/photos/home-hero").file(file).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value("home-hero"))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("http://cdn.test/photos/site/home-hero/")));

        var putReq = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        var putBody = org.mockito.ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(putReq.capture(), putBody.capture());
        // Stored object is a resized JPEG, not the original PNG
        org.assertj.core.api.Assertions.assertThat(putReq.getValue().contentType()).isEqualTo("image/jpeg");
        org.assertj.core.api.Assertions.assertThat(putReq.getValue().key()).endsWith(".jpg");
        var stored = javax.imageio.ImageIO.read(putBody.getValue().contentStreamProvider().newStream());
        org.assertj.core.api.Assertions.assertThat(stored.getWidth()).isEqualTo(1800);
        org.assertj.core.api.Assertions.assertThat(stored.getHeight()).isEqualTo(1200);

        // A fresh upload is a draft: the public map doesn't have it until Publish.
        mvc.perform(get("/api/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['home-hero']").doesNotExist());
        mvc.perform(get("/api/admin/publish").header("Authorization", auth))
                .andExpect(jsonPath("$.dirty").value(true))
                .andExpect(jsonPath("$.changes.photos").value(true)); // pricing may be dirty too: tests share one context
        mvc.perform(post("/api/admin/publish").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dirty").value(false));
        mvc.perform(get("/api/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['home-hero']").exists());

        // Portfolio slots are open-ended: filling pf-families-1 makes pf-families-2 appear; pf-families-99 is out of range.
        mvc.perform(multipart("/api/admin/photos/pf-families-1").file(file).header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/photos").header("Authorization", auth))
                .andExpect(jsonPath("$[?(@.title=='Portfolio · Families & solo')].slots.length()").value(2))
                .andExpect(jsonPath("$[?(@.title=='Portfolio · Families & solo')].slots[1].id").value("pf-families-2"));
        mvc.perform(multipart("/api/admin/photos/pf-families-99").file(file).header("Authorization", auth)).andExpect(status().isBadRequest());

        // Unknown slot and non-image content are both refused.
        mvc.perform(multipart("/api/admin/photos/not-a-slot").file(file).header("Authorization", auth))
                .andExpect(status().isBadRequest());
        var text = new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1});
        mvc.perform(multipart("/api/admin/photos/home-feat-1").file(text).header("Authorization", auth))
                .andExpect(status().isBadRequest());
        // Claims to be an image but isn't decodable
        var junk = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/admin/photos/home-feat-1").file(junk).header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    private static byte[] pngOfSize(int w, int h) throws Exception {
        var img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
