package com.dazo66.controller;

import com.dazo66.config.SecurityFilter;
import com.dazo66.security.*;
import com.dazo66.service.AppReleaseStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import javax.servlet.http.Cookie;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppUpdateControllerTest {
    @TempDir Path root;
    UserTestSupport users;
    AppReleaseStore store;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();
    Cookie admin;
    String csrf;
    byte[] apk;

    @BeforeEach void setup() throws Exception {
        users = new UserTestSupport();
        store = new AppReleaseStore(root.toString(), json);
        mvc = MockMvcBuilders.standaloneSetup(new AppUpdateController(store))
                .setControllerAdvice(new AclExceptionHandler()).addFilters(new SecurityFilter(users.tokens)).build();
        String token = users.tokens.createToken("admin");
        admin = new Cookie(users.tokens.getCookieName(), token); csrf = users.tokens.csrf(users.tokens.resolve(token));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : Arrays.asList("AndroidManifest.xml", "classes.dex")) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(name.getBytes("UTF-8")); zip.closeEntry();
            }
        }
        apk = bytes.toByteArray();
    }
    @AfterEach void done() { users.close(); }

    ObjectNode manifest(String version, long code) throws Exception {
        StringBuilder hash = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(apk)) hash.append(String.format("%02x", b & 255));
        return json.createObjectNode().put("versionName", version).put("versionCode", code)
                .put("size", apk.length).put("sha256", hash.toString()).put("packageName", "com.dazo66.android")
                .put("minSdk", 26).put("certificateSha256", "6a79b1bb5e1777dc936d71abd9b6e370e0c782760f064525057250e4e548ced5");
    }
    void publish(String version, long code) throws Exception {
        mvc.perform(multipart("/api/app-updates").file(new MockMultipartFile("file", "app.apk", "application/octet-stream", apk))
                        .param("manifest", manifest(version, code).toString()).param("description", "新功能\n修复问题")
                        .cookie(admin).header("X-CSRF-Token", csrf))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.latestVersion").value(version));
    }
    @Test void requiresLoginForEveryEndpointAndCsrfForPublishing() throws Exception {
        mvc.perform(get("/api/app-updates/latest")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/app-updates/1.4.0.21/apk")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app-updates")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app-updates").cookie(admin)).andExpect(status().isForbidden());
    }
    @Test void publishesTwoFieldLatestJsonAndAuthenticatedDownloadWithResume() throws Exception {
        mvc.perform(get("/api/app-updates/latest").cookie(admin)).andExpect(jsonPath("$.data.latestVersion").value(""));
        publish("1.4.0.21", 100021);
        assertThat(json.readTree(root.resolve("latest.json").toFile()).size()).isEqualTo(2);
        assertThat(Files.readAllBytes(root.resolve("1.4.0.21/DazoGallery.apk"))).isEqualTo(apk);
        mvc.perform(get("/api/app-updates/latest").cookie(admin)).andExpect(jsonPath("$.data.description").value("新功能\n修复问题"));
        mvc.perform(get("/api/app-updates/1.4.0.21/apk").cookie(admin)).andExpect(status().isOk())
                .andExpect(header().string("X-App-Version-Code", "100021"))
                .andExpect(header().string("X-App-Sha256", manifest("1.4.0.21", 100021).path("sha256").asText()))
                .andExpect(content().bytes(apk));
        mvc.perform(head("/api/app-updates/1.4.0.21/apk").cookie(admin)).andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", apk.length)).andExpect(content().bytes(new byte[0]));
        mvc.perform(get("/api/app-updates/1.4.0.21/apk").cookie(admin).header("Range", "bytes=5-12"))
                .andExpect(status().isPartialContent()).andExpect(content().bytes(Arrays.copyOfRange(apk, 5, 13)));
    }
    @Test void readersCanUpdateButCannotPublish() throws Exception {
        publish("1.4.0.21", 100021); users.create("reader", "read:D:/pictures/*");
        String token = users.tokens.createToken("reader"); Cookie cookie = new Cookie(users.tokens.getCookieName(), token);
        mvc.perform(get("/api/app-updates/latest").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/app-updates/1.4.0.21/apk").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(multipart("/api/app-updates").file(new MockMultipartFile("file", apk))
                        .param("manifest", manifest("1.4.0.22", 100022).toString()).param("description", "update")
                        .cookie(cookie).header("X-CSRF-Token", users.tokens.csrf(users.tokens.resolve(token))))
                .andExpect(status().isForbidden());
        assertThat(store.latest().path("latestVersion").asText()).isEqualTo("1.4.0.21");
    }
    @Test void idempotentRetriesNeverReplaceExistingApkOrDowngradeLatest() throws Exception {
        publish("1.4.0.21", 100021); publish("1.4.0.21", 100021); publish("1.4.0.22", 100022);
        store.upload(new MockMultipartFile("file", apk), manifest("1.4.0.21", 100021).toString(), "新功能\n修复问题");
        assertThat(store.latest().path("latestVersion").asText()).isEqualTo("1.4.0.22");
        assertThatThrownBy(() -> store.upload(new MockMultipartFile("file", apk), manifest("1.4.0.21", 100021).toString(), "替换描述"))
                .hasMessageContaining("409");
        assertThatThrownBy(() -> store.upload(new MockMultipartFile("file", apk), manifest("1.4.0.19", 100019).toString(), "旧版本"))
                .hasMessageContaining("409");
        assertThat(Files.readAllBytes(root.resolve("1.4.0.21/DazoGallery.apk"))).isEqualTo(apk);
    }
    @Test void rejectsCorruptedUploadsAndTraversalWithoutPublishingPartialState() throws Exception {
        ObjectNode bad = manifest("1.4.0.21", 100021).put("sha256", String.join("", Collections.nCopies(64, "a")));
        assertThatThrownBy(() -> store.upload(new MockMultipartFile("file", apk), bad.toString(), "坏包")).hasMessageContaining("校验");
        assertThatThrownBy(() -> store.upload(new MockMultipartFile("file", apk), manifest("1.4.0.21", 100021).put("versionName", "../escape").toString(), "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.upload(new MockMultipartFile("file", apk), manifest("1.4.0.21", 100021).put("packageName", "another.app").toString(), "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.latest().path("latestVersion").asText()).isEmpty();
        try (java.util.stream.Stream<Path> files = Files.list(root)) { assertThat(files.count()).isZero(); }
    }
    @Test void revokedPermanentUploadSessionIsRejected() throws Exception {
        assertThat(users.tokens.resolve(admin.getValue())).isNotNull(); users.tokens.logout(admin.getValue().split("\\.")[1]);
        mvc.perform(get("/api/app-updates/latest").cookie(admin)).andExpect(status().isUnauthorized());
    }
}
