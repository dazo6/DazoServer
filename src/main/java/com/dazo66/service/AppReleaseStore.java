package com.dazo66.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Immutable version directories; latest.json is committed only after the entire APK is durable. */
@Service
public class AppReleaseStore {
    public static final long MAX_APK_BYTES = 256L * 1024 * 1024;
    private static final String PACKAGE = "com.dazo66.android";
    private static final String CERTIFICATE = "6a79b1bb5e1777dc936d71abd9b6e370e0c782760f064525057250e4e548ced5";
    private final Path root;
    private final ObjectMapper json;

    public AppReleaseStore(@Value("${app.releases.path:data/app-releases}") String directory, ObjectMapper json) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        this.json = json;
    }

    public ObjectNode latest() throws IOException {
        Path file = root.resolve("latest.json");
        if (!Files.exists(file)) return json.createObjectNode().put("latestVersion", "").put("description", "");
        JsonNode value = json.readTree(file.toFile());
        if (!value.isObject() || value.size() != 2 || !value.path("latestVersion").isTextual()
                || !value.path("description").isTextual()) throw new IOException("最新版本信息损坏");
        metadata(value.get("latestVersion").asText());
        return (ObjectNode) value;
    }

    public ObjectNode metadata(String version) throws IOException {
        Path directory = versionDirectory(version);
        Path file = directory.resolve("manifest.json"), apk = directory.resolve("DazoGallery.apk");
        if (!Files.isRegularFile(file) || !Files.isRegularFile(apk))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "此版本尚未发布");
        ObjectNode value = validate(json.readTree(file.toFile()));
        if (!version.equals(value.path("versionName").asText()) || Files.size(apk) != value.path("size").asLong())
            throw new IOException("安装包信息损坏");
        return value;
    }

    public Path apk(String version) throws IOException {
        metadata(version);
        return versionDirectory(version).resolve("DazoGallery.apk");
    }

    public ObjectNode upload(MultipartFile file, String manifest, String description) throws IOException {
        if (description == null || description.length() > 16000)
            throw new IllegalArgumentException("更新描述最长16000字符");
        ObjectNode info;
        try { info = validate(json.readTree(manifest)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("版本信息无效"); }
        if (file.isEmpty() || file.getSize() > MAX_APK_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "APK必须在1字节到256MiB之间");
        if (file.getSize() != info.path("size").asLong()) throw new IllegalArgumentException("APK大小与版本信息不一致");
        Files.createDirectories(root);
        Path staging = Files.createTempDirectory(root, ".upload-");
        try {
            Path apk = staging.resolve("DazoGallery.apk");
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
            long count = 0;
            try (InputStream input = file.getInputStream(); FileOutputStream output = new FileOutputStream(apk.toFile())) {
                byte[] buffer = new byte[65536]; int length;
                while ((length = input.read(buffer)) != -1) {
                    count += length;
                    if (count > info.path("size").asLong() || count > MAX_APK_BYTES)
                        throw new IllegalArgumentException("APK大小与版本信息不一致");
                    digest.update(buffer, 0, length); output.write(buffer, 0, length);
                }
                output.getFD().sync();
            }
            if (count != info.path("size").asLong() || !hex(digest.digest()).equals(info.path("sha256").asText()))
                throw new IllegalArgumentException("APK校验失败");
            try (ZipFile archive = new ZipFile(apk.toFile())) {
                if (archive.getEntry("AndroidManifest.xml") == null || archive.getEntry("classes.dex") == null)
                    throw new IllegalArgumentException("请选择有效的Android安装包");
            }
            info.put("description", description);
            write(staging.resolve("manifest.json"), info);
            return publish(staging, info);
        } finally {
            Files.deleteIfExists(staging.resolve("DazoGallery.apk"));
            Files.deleteIfExists(staging.resolve("manifest.json"));
            Files.deleteIfExists(staging);
        }
    }

    private synchronized ObjectNode publish(Path staging, ObjectNode info) throws IOException {
        String version = info.path("versionName").asText();
        Path destination = versionDirectory(version);
        ObjectNode current = latest();
        if (Files.exists(destination)) {
            ObjectNode existing = metadata(version);
            for (String key : Arrays.asList("sha256", "versionCode", "size", "packageName", "minSdk", "certificateSha256", "description"))
                if (!Objects.equals(existing.get(key), info.get(key)))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "此版本已发布，不能替换其安装包或描述");
        } else {
            if (!current.path("latestVersion").asText().isEmpty()
                    && info.path("versionCode").asLong() <= metadata(current.path("latestVersion").asText()).path("versionCode").asLong())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "版本号必须高于当前已发布版本");
            move(staging, destination, false);
        }
        if (current.path("latestVersion").asText().isEmpty()
                || info.path("versionCode").asLong() >= metadata(current.path("latestVersion").asText()).path("versionCode").asLong()) {
            ObjectNode latest = json.createObjectNode().put("latestVersion", version).put("description", info.path("description").asText());
            Path temporary = Files.createTempFile(root, ".latest-", ".tmp");
            try { write(temporary, latest); move(temporary, root.resolve("latest.json"), true); }
            finally { Files.deleteIfExists(temporary); }
        }
        return latest();
    }

    private ObjectNode validate(JsonNode value) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("版本信息无效");
        String version = value.path("versionName").asText(); versionDirectory(version);
        if (!PACKAGE.equals(value.path("packageName").asText()) || !CERTIFICATE.equals(value.path("certificateSha256").asText()))
            throw new IllegalArgumentException("安装包名称或签名不匹配");
        if (!value.path("versionCode").isIntegralNumber() || value.path("versionCode").asLong() < 1
                || value.path("versionCode").asLong() > 2100000000L || value.path("minSdk").asInt() < 26
                || !value.path("size").isIntegralNumber() || value.path("size").asLong() < 1
                || value.path("size").asLong() > MAX_APK_BYTES || !value.path("sha256").asText().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("安装包版本或校验信息无效");
        ObjectNode result = json.createObjectNode();
        for (String key : Arrays.asList("versionName", "versionCode", "packageName", "minSdk", "size", "sha256", "certificateSha256")) result.set(key, value.get(key));
        if (value.has("description")) result.set("description", value.get("description"));
        return result;
    }

    private Path versionDirectory(String version) {
        if (version == null || !version.matches("[0-9]{1,6}(?:\\.[0-9]{1,6}){2,3}")) throw new IllegalArgumentException("版本号无效");
        return root.resolve(version);
    }
    private void write(Path file, JsonNode value) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
            out.write(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(value)); out.getFD().sync();
        }
    }
    private static void move(Path from, Path to, boolean replace) throws IOException {
        try {
            if (replace) Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            if (replace) Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); else Files.move(from, to);
        }
    }
    private static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 255)); return s.toString(); }
}
