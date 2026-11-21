package com.dazo66.android;

import android.content.Context;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ApiClient {
    private static ApiClient instance;
    static synchronized ApiClient get(Context context) {
        if (instance == null) instance = new ApiClient(context.getApplicationContext());
        return instance;
    }
    private volatile Done<String> connectionListener;
    void setConnectionListener(Done<String> listener) { connectionListener = listener; }
    String connectionState() {
        try { return new JSONObject().put("host", host).put("activeHost", routes == null ? host : routes.active()).put("backupUrls", backupUrls()).toString(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private void routingChanged() {
        Done<String> listener = connectionListener;
        if (listener != null) post(listener, connectionState(), null);
    }
    private void connectRoutes(ServerRoutes value) { value.setListener(this::routingChanged); routes = value; routingChanged(); }
    interface Done<T> { void accept(T value, Exception error); }
    interface Progress { void accept(int percent); }
    private static final long CACHE_LIMIT = 256L * 1024 * 1024;
    private static final long CACHE_TTL = 30L * 24 * 3600 * 1000;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final ExecutorService mediaPool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final File imageCache;
    private final MediaVersions mediaVersions = new MediaVersions();
    private volatile String host;
    private volatile ServerRoutes routes;
    private volatile String cookie = "";
    private volatile String csrf = "";

    ApiClient(Context context) {
        prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE);
        imageCache = new File(context.getCacheDir(), "media");
        imageCache.mkdirs();
        host = prefs.getString("host", "");
        try { if (!host.isEmpty()) routes = new ServerRoutes(host, strings(new JSONArray(prefs.getString("backups", "[]")))); }
        catch (Exception ignored) { routes = host.isEmpty() ? null : new ServerRoutes(host, java.util.Collections.emptyList()); }
        if (routes != null) routes.setListener(this::routingChanged);
        new NetworkMonitor(context, () -> { ServerRoutes value = routes; if (value != null) value.resetToPrimary(); });
        try { cookie = decrypt(prefs.getString("session", "")); }
        catch (Exception ignored) { prefs.edit().remove("session").apply(); cookie = ""; }
        pool.execute(this::trimCache);
    }

    String host() { return host; }
    String activeHost() { return routes == null ? host : routes.active(); }
    String lastUser() { return prefs.getString("user", ""); }
    String scope() { return host + "|" + lastUser(); }
    boolean signedIn() { return !cookie.isEmpty(); }
    String cookie() { return cookie; }
    /** Update credentials only go to a configured server; redirects are never followed. */
    HttpURLConnection updateConnection(String method, String path, long offset, String ownerScope) throws Exception {
        if (!signedIn() || !ownerScope.equals(scope())) throw new IllegalStateException("请先登录服务器后检查更新");
        if (!path.startsWith("/api/app-updates/")) throw new IllegalArgumentException("更新接口路径无效");
        ServerRoutes selected = routes;
        final String session = cookie;
        return selected.execute(path, (address, trusted) -> {
            if (routes != selected || !ownerScope.equals(scope()) || !session.equals(cookie) || session.isEmpty() || !trusted || !selected.trusted(address))
                throw new IllegalStateException("服务器登录状态已变化，请重新检查更新");
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod(method); connection.setConnectTimeout(6000); connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Cookie", session);
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-");
            try {
                int status = connection.getResponseCode();
                if (status == 502 || status == 503 || status == 504) throw new java.io.IOException("HTTP " + status);
                if (status == 401) throw new IllegalStateException("登录已失效，请重新登录后检查更新");
                if (status == 404) throw new IllegalStateException("服务器尚未提供此应用更新，请稍后重试");
                if (status != 200 && status != 206) throw new IllegalStateException("更新服务暂不可用（HTTP " + status + "）");
                return connection;
            } catch (Exception e) {
                connection.disconnect();
                if (e instanceof java.io.IOException) throw new ServerRoutes.Failure("连接更新服务器失败", e, true);
                throw e;
            }
        });
    }
    JSONObject latestUpdate(String ownerScope) throws Exception {
        HttpURLConnection connection = updateConnection("GET", "/api/app-updates/latest", 0, ownerScope);
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (out.size() + count > 65536) throw new IllegalArgumentException("更新信息过大");
                out.write(buffer, 0, count);
            }
            JSONObject value = new JSONObject(out.toString("UTF-8"));
            if (!"SUCCESS".equals(value.optString("result"))) throw new IllegalStateException(value.optString("message", "检查更新失败"));
            return value.getJSONObject("data");
        } finally { connection.disconnect(); }
    }
    String absolute(String path) { return routes == null ? path : routes.addresses(path).get(0); }
    boolean trusted(String path) { return routes != null && routes.trusted(path); }
    static final class HttpStream implements java.io.Closeable {
        final HttpURLConnection connection; final InputStream input; final long length; final String address; final ServerRoutes owner; final long epoch;
        HttpStream(HttpURLConnection connection, InputStream input, long length, String address, ServerRoutes owner, long epoch) { this.connection = connection; this.input = input; this.length = length; this.address = address; this.owner = owner; this.epoch = epoch; }
        @Override public void close() throws java.io.IOException { try { input.close(); } finally { connection.disconnect(); } }
    }
    void streamFailed(HttpStream stream) { if (stream != null && routes == stream.owner) stream.owner.failed(stream.address, stream.epoch); }
    HttpStream openStream(String path, long offset, long length) throws Exception {
        ServerRoutes selected = routes;
        if (selected == null) throw new IllegalStateException("请先登录服务器");
        long epoch = selected.generation();
        return selected.execute(path, (address, trusted) -> {
            for (int redirects = 0; redirects < 6; redirects++) {
                URL url = new URL(address);
                if (!("http".equals(url.getProtocol()) || "https".equals(url.getProtocol()))) throw new IllegalArgumentException("媒体地址无效");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(6000); connection.setReadTimeout(15000); connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (offset > 0 || length >= 0) connection.setRequestProperty("Range", "bytes=" + offset + "-" + (length >= 0 ? offset + length - 1 : ""));
                if (trusted && selected.trusted(address) && !cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
                try {
                    int code = connection.getResponseCode();
                    if (code >= 300 && code < 400 && connection.getHeaderField("Location") != null) {
                        address = new URL(url, connection.getHeaderField("Location")).toString();
                        trusted = trusted && selected.trusted(address); connection.disconnect(); continue;
                    }
                    if (code == 502 || code == 503 || code == 504) throw new java.io.IOException("HTTP " + code);
                    if (code != 200 && code != 206) throw new IllegalArgumentException("读取媒体失败（HTTP " + code + "）");
                    if (code == 206 && (connection.getHeaderField("Content-Range") == null || !connection.getHeaderField("Content-Range").startsWith("bytes " + offset + "-"))) throw new java.io.IOException("媒体范围不一致");
                    InputStream input = connection.getInputStream();
                    long available = connection.getContentLengthLong();
                    if (code == 200 && offset > 0) {
                        long remaining = offset; byte[] skip = new byte[65536];
                        while (remaining > 0) { int count = input.read(skip, 0, (int)Math.min(remaining, skip.length)); if (count < 0) throw new java.io.EOFException(); remaining -= count; }
                        if (available >= 0) available = Math.max(0, available - offset);
                    }
                    return new HttpStream(connection, input, length >= 0 ? (available >= 0 ? Math.min(available, length) : length) : available, address, selected, epoch);
                } catch (Exception error) {
                    connection.disconnect();
                    if (error instanceof java.io.IOException) throw new ServerRoutes.Failure("连接媒体服务器失败", error, true);
                    throw error;
                }
            }
            throw new IllegalArgumentException("媒体重定向过多");
        });
    }
    java.util.List<String> mediaAddresses(String path) { return routes.addresses(path); }
    JSONArray backupUrls() { return new JSONArray(routes == null ? java.util.Collections.emptyList() : routes.backups()); }
    void resolveDownload(String path, Done<String> done) {
        pool.execute(() -> {
            try {
                if (!trusted(path)) throw new IllegalArgumentException("下载地址不属于配置的服务器");
                String result = routes.execute(path, (address, trusted) -> {
                    HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
                    connection.setConnectTimeout(6000); connection.setReadTimeout(15000); connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("Range", "bytes=0-0");
                    if (!cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
                    try {
                        int status = connection.getResponseCode();
                        if (status == 502 || status == 503 || status == 504) throw new java.io.IOException("HTTP " + status);
                        if (status >= 400) throw new IllegalArgumentException("下载暂不可用（HTTP " + status + "）");
                        return address;
                    } catch (java.io.IOException error) { throw new ServerRoutes.Failure("连接下载服务器失败", error, true); }
                    finally { connection.disconnect(); }
                });
                post(done, result, null);
            } catch (Exception error) { post(done, null, error); }
        });
    }
    private static ArrayList<String> strings(JSONArray values) throws Exception {
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) result.add(values.getString(i));
        return result;
    }
    void setBackupUrls(JSONArray values) throws Exception {
        connectRoutes(new ServerRoutes(host, strings(values)));
        prefs.edit().putString("backups", backupUrls().toString()).apply();
    }

    void login(String rawHost, String backups, String user, String password, Done<JSONObject> done) {
        pool.execute(() -> {
            try {
                ServerRoutes selected = new ServerRoutes(rawHost, strings(new JSONArray(backups)));
                host = selected.primary(); cookie = ""; csrf = ""; connectRoutes(selected);
                prefs.edit().putString("host", host).putString("backups", backupUrls().toString()).putString("user", user.trim()).apply();
                JSONObject body = new JSONObject().put("userId", user.trim()).put("password", password);
                JSONObject data = send("POST", "/api/auth/login", body).getJSONObject("data");
                csrf = data.optString("csrfToken");
                prefs.edit().putString("host", host).putString("user", user.trim()).putString("session", encrypt(cookie)).apply();
                post(done, data.getJSONObject("me"), null);
            } catch (Exception e) { cookie = ""; csrf = ""; prefs.edit().remove("session").apply(); post(done, null, e); }
        });
    }

    void restore(Done<JSONObject> done) {
        pool.execute(() -> {
            try {
                JSONObject user = send("GET", "/api/auth/me", null).getJSONObject("data");
                csrf = send("GET", "/api/auth/csrf", null).optString("data");
                post(done, user, null);
            } catch (Exception e) { android.util.Log.w("DazoApi", "Session restore failed", e); csrf = ""; post(done, null, e); }
        });
    }

    void request(String method, String path, JSONObject body, Done<Object> done) {
        pool.execute(() -> {
            try { post(done, send(method, path, body).opt("data"), null); }
            catch (Exception e) { android.util.Log.w("DazoApi", "API request failed", e); post(done, null, e); }
        });
    }

    AtomicBoolean upload(Context context, Uri uri, String filename, long size, String folderPath, Progress progress, Done<Object> done) {
        AtomicBoolean cancelled = new AtomicBoolean();
        pool.execute(() -> {
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalArgumentException("无法读取文件");
                if (size < 1 || size > 2L * 1024 * 1024 * 1024) throw new IllegalArgumentException("文件大小必须在 1 字节到 2GB 之间");
                final int chunkSize = 512 * 1024;
                int count = (int) ((size + chunkSize - 1) / chunkSize);
                String uploadId = "";
                for (int i = 0; i < count; i++) {
                    if (cancelled.get()) throw new IllegalStateException("上传已取消");
                    int wanted = (int) Math.min(chunkSize, size - (long) i * chunkSize);
                    byte[] part = new byte[wanted]; int offset = 0;
                    while (offset < wanted) {
                        int n = input.read(part, offset, wanted - offset);
                        if (n < 0) throw new IllegalStateException("文件读取中断");
                        offset += n;
                    }
                    String path;
                    if (count == 1) path = "/api/fanbox/gallery/upload?folderPath=" + encode(folderPath);
                    else path = "/api/fanbox/gallery/upload-chunk?folderPath=" + encode(folderPath)
                            + "&filename=" + encode(filename) + "&totalSize=" + size + "&partIndex=" + i
                            + "&partCount=" + count + (uploadId.isEmpty() ? "" : "&uploadId=" + encode(uploadId));
                    JSONObject result = multipart(path, filename, part);
                    uploadId = result.optJSONObject("data") == null ? uploadId : result.getJSONObject("data").optString("uploadId", uploadId);
                    int percent = (int) Math.round((i + 1) * 100.0 / count);
                    main.post(() -> progress.accept(percent));
                }
                post(done, true, null);
            } catch (Exception e) { post(done, null, e); }
        });
        return cancelled;
    }

    void uploadAvatar(Context context, Uri uri, Done<Object> done) {
        pool.execute(() -> {
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                byte[] bytes = readAll(stream);
                if (bytes.length > 2 * 1024 * 1024) throw new IllegalArgumentException("头像最大 2MiB");
                multipart("/api/auth/me/avatar", "avatar.png", bytes);
                post(done, true, null);
            } catch (Exception e) { post(done, null, e); }
        });
    }

    private JSONObject multipart(String path, String filename, byte[] data) throws Exception {
        String boundary = "DazoAndroid" + System.nanoTime();
        String safe = filename.replace("\"", "_").replace("\\", "_").replace("\r", "_").replace("\n", "_");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + safe + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(data); bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] response = raw("POST", host + path, bytes.toByteArray(), "multipart/form-data; boundary=" + boundary);
        JSONObject result = new JSONObject(new String(response, StandardCharsets.UTF_8));
        if (!"SUCCESS".equals(result.optString("result"))) throw new IllegalStateException(result.optString("message", "上传失败"));
        return result;
    }

    private static String encode(String value) { try { return java.net.URLEncoder.encode(value, "UTF-8"); } catch (java.io.UnsupportedEncodingException e) { throw new IllegalStateException(e); } }

    void media(String url, boolean force, Done<JSONObject> done) {
        mediaPool.execute(() -> {
            File file = null;
            try {
                String address = absolute(url), cacheKey = hash(scope() + routes.canonical(url));
                file = new File(imageCache, cacheKey);
                boolean replaced = false;
                if (!file.isFile() || force) {
                    try {
                        byte[] bytes = raw("GET", address, null);
                        if (bytes.length > 64 * 1024 * 1024) throw new IllegalArgumentException("单张图片超过 64 MiB");
                        BitmapFactory.Options check = new BitmapFactory.Options(); check.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, check);
                        if (check.outWidth < 1 || check.outHeight < 1) throw new IllegalArgumentException("图片格式不支持");
                        File temporary = new File(imageCache, cacheKey + ".tmp-" + Thread.currentThread().getId());
                        try (FileOutputStream stream = new FileOutputStream(temporary)) { stream.write(bytes); }
                        java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        replaced = true;
                    } catch (java.io.IOException failure) { if (!file.isFile()) throw failure; }
                }
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                if (bounds.outWidth < 1 || bounds.outHeight < 1) { file.delete(); throw new IllegalArgumentException("图片格式不支持"); }
                file.setLastModified(System.currentTimeMillis()); trimCache();
                post(done, new JSONObject().put("uri", Uri.fromFile(file).toString()+"?v="+mediaVersions.version(cacheKey, replaced)).put("width", bounds.outWidth).put("height", bounds.outHeight).put("animated", "image/gif".equals(bounds.outMimeType) || "image/webp".equals(bounds.outMimeType)), null);
            } catch (Exception e) { post(done, null, e); }
        });
    }

    long cacheBytes() {
        File[] files = imageCache.listFiles(); long size = 0;
        if (files != null) for (File file : files) if (!file.getName().contains(".tmp-")) size += file.length();
        return size;
    }
    void clearCache(Done<Object> done) {
        pool.execute(() -> { File[] files = imageCache.listFiles(); if (files != null) for (File file : files) file.delete(); mediaVersions.clear(); post(done, true, null); });
    }

    void image(String url, Done<Bitmap> done) {
        if (url == null || url.isEmpty()) { post(done, null, null); return; }
        pool.execute(() -> {
            try {
                String absolute = absolute(url);
                String key = hash(scope() + routes.canonical(url));
                File file = new File(imageCache, key);
                byte[] bytes;
                if (file.isFile()) {
                    bytes = java.nio.file.Files.readAllBytes(file.toPath());
                    file.setLastModified(System.currentTimeMillis());
                } else {
                    bytes = raw("GET", absolute, null);
                    if (bytes.length <= 64 * 1024 * 1024) {
                        File temporary = new File(imageCache, key + ".tmp-" + Thread.currentThread().getId());
                        try (FileOutputStream stream = new FileOutputStream(temporary)) { stream.write(bytes); }
                        java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        trimCache();
                    }
                }
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                int sample = 1;
                while (options.outWidth / sample > 2400 || options.outHeight / sample > 2400) sample *= 2;
                options.inSampleSize = sample; options.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (bitmap == null) file.delete();
                post(done, bitmap, bitmap == null ? new IllegalArgumentException("图片格式不支持") : null);
            } catch (Exception e) { post(done, null, e); }
        });
    }

    private JSONObject send(String method, String path, JSONObject body) throws Exception {
        byte[] bytes = raw(method, host + path, body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8), "application/json; charset=UTF-8");
        JSONObject response = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (!"SUCCESS".equals(response.optString("result")))
            throw new IllegalStateException(response.optString("message", "请求失败"));
        return response;
    }

    private byte[] raw(String method, String address, byte[] body) throws Exception { return raw(method, address, body, "application/json; charset=UTF-8"); }
    private byte[] raw(String method, String address, byte[] body, String contentType) throws Exception {
        ServerRoutes selected = routes;
        if (selected == null) throw new IllegalArgumentException("请先填写服务器地址");
        return selected.execute(address, (target, trusted) -> raw(method, target, body, contentType, 0, trusted));
    }
    private byte[] raw(String method, String address, byte[] body, String contentType, int redirects, boolean trusted) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json, image/*, */*");
        boolean sameOrigin = trusted && routes.trusted(address);
        if (sameOrigin && !cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
        if (sameOrigin && !csrf.isEmpty() && !"GET".equals(method)) connection.setRequestProperty("X-CSRF-Token", csrf);
        boolean sent = false;
        boolean retrySafe = "GET".equals(method) || "/api/auth/login".equals(URI.create(address).getPath());
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", contentType);
                try (java.io.OutputStream output = connection.getOutputStream()) { sent = true; output.write(body); }
            }
            if (body == null && !"GET".equals(method)) {
                connection.connect(); // A failed connection can switch origins before sending a write.
                sent = true;
            }
            int status = connection.getResponseCode();
            if ("GET".equals(method) && status >= 300 && status < 400 && connection.getHeaderField("Location") != null) {
                if (redirects >= 5) throw new IllegalStateException("图片重定向过多");
                String next = new URL(new URL(address), connection.getHeaderField("Location")).toString();
                connection.disconnect();
                return raw(method, next, null, contentType, redirects + 1, trusted && routes.trusted(next));
            }
            String setCookie = connection.getHeaderField("Set-Cookie");
            if (sameOrigin && setCookie != null && !setCookie.isEmpty()) {
                cookie = setCookie.split(";", 2)[0];
                if (!prefs.getString("user", "").isEmpty()) prefs.edit().putString("session", encrypt(cookie)).apply();
            }
            if (status == 502 || status == 503 || status == 504) throw new ServerRoutes.Failure("服务器暂不可用（HTTP " + status + "）", null, retrySafe);
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] bytes = readAll(stream);
            if (sameOrigin && status == 401) { cookie = ""; prefs.edit().remove("session").apply(); }
            if (status >= 300) {
                try { throw new IllegalStateException(new JSONObject(new String(bytes, StandardCharsets.UTF_8)).optString("message", "HTTP " + status)); }
                catch (org.json.JSONException ignored) { throw new IllegalStateException("HTTP " + status); }
            }
            return bytes;
        } catch (java.io.IOException e) {
            if (e instanceof ServerRoutes.Failure) throw e;
            throw new ServerRoutes.Failure(sent && !retrySafe ? "连接中断，操作结果尚未确认，请刷新后重试" : "连接服务器失败", e, retrySafe || !sent);
        } finally { connection.disconnect(); }
    }

    void logout(Done<Object> done) {
        request("POST", "/api/auth/logout", null, (value, error) -> {
            cookie = ""; csrf = ""; prefs.edit().remove("session").apply();
            done.accept(value, error);
        });
    }

    private static byte[] readAll(InputStream stream) throws Exception {
        if (stream == null) return new byte[0];
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32768]; int n;
            while ((n = input.read(buffer)) >= 0) { if (output.size() + n > 64 * 1024 * 1024) throw new IllegalArgumentException("响应超过 64 MiB"); output.write(buffer, 0, n); }
            return output.toByteArray();
        }
    }

    private static String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias("dazo_session")) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("dazo_session", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey("dazo_session", null);
    }
    private String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV(), encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] packed = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, packed, 0, iv.length); System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
        return Base64.encodeToString(packed, Base64.NO_WRAP);
    }
    private String decrypt(String encoded) throws Exception {
        if (encoded.isEmpty()) return "";
        byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
        byte[] iv = Arrays.copyOfRange(packed, 0, 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(packed, 12, packed.length - 12), StandardCharsets.UTF_8);
    }

    private synchronized void trimCache() {
        File[] files = imageCache.listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis(), total = 0;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (file.getName().contains(".tmp-")) { if (now - file.lastModified() > 3600_000) file.delete(); continue; }
            if (now - file.lastModified() > CACHE_TTL && file.delete()) continue;
            total += file.length();
        }
        for (File file : files) {
            if (total <= CACHE_LIMIT) break;
            if (!file.getName().contains(".tmp-") && file.exists()) { long bytes = file.length(); if (file.delete()) total -= bytes; }
        }
    }

    private <T> void post(Done<T> done, T value, Exception error) { main.post(() -> done.accept(value, error)); }
}
