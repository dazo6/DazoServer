package com.dazo66.android;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Public releases, resumable app-owned downloads, and Android's installation confirmation. */
final class UpdateManager {
    interface Listener { void changed(String json); }
    interface Progress { void changed(long downloaded, long total); }
    private static UpdateManager instance;
    static synchronized UpdateManager get(Context context) {
        if (instance == null) instance = new UpdateManager(context);
        return instance;
    }
    private static final long CHECK_INTERVAL = 6L * 60 * 60 * 1000;
    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean checking = new AtomicBoolean();
    private final AtomicBoolean transferring = new AtomicBoolean();
    private final long installedCode;
    private final String installedName;
    private final File directory;
    private volatile Listener listener;
    private volatile JSONObject candidate;
    private volatile String phase = "idle", message = "", error = "";
    private volatile long downloaded = 0;
    private volatile boolean visible, awaitingPermission, cancelled;
    private volatile HttpURLConnection activeConnection;

    private UpdateManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("app-updates", 0);
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            installedCode = versionCode(info); installedName = info.versionName;
        } catch (Exception e) { throw new IllegalStateException(e); }
        directory = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates");
        directory.mkdirs();
        try { candidate = new JSONObject(prefs.getString("candidate", "{}")); } catch (Exception ignored) { candidate = new JSONObject(); }
        worker.execute(() -> {
            try {
                long legacy = prefs.getLong("downloadId", -1);
                if (legacy >= 0) {
                    ((DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE)).remove(legacy);
                    prefs.edit().remove("downloadId").putBoolean("active", true).commit();
                }
                if (candidate.optLong("versionCode") <= installedCode) { discardDownload(); candidate = new JSONObject(); prefs.edit().remove("candidate").apply(); }
                else validateManifest(candidate);
                recover();
            } catch (Exception e) { fail(e); }
        });
    }

    synchronized String state() {
        try {
            return new JSONObject().put("phase", phase).put("message", message).put("error", error)
                    .put("versionCode", installedCode).put("versionName", installedName)
                    .put("autoCheck", prefs.getBoolean("autoCheck", true))
                    .put("lastCheckedAt", prefs.getLong("lastCheckedAt", 0))
                    .put("candidate", candidate).put("downloadedBytes", downloaded)
                    .put("totalBytes", candidate.optLong("size")).toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    void setListener(Listener value) { listener = value; emit(); }
    private void emit() { String value = state(); main.post(() -> { Listener target = listener; if (target != null) target.changed(value); }); }
    void setAutoCheck(boolean value) { prefs.edit().putBoolean("autoCheck", value).apply(); emit(); }
    void setVisible(boolean value) {
        visible = value;
        if (value) worker.execute(() -> { try { recover(); } catch (Exception e) { fail(e); } });
    }
    void check(boolean manual) {
        if (!manual && (!prefs.getBoolean("autoCheck", true) || System.currentTimeMillis() - prefs.getLong("lastCheckedAt", 0) < CHECK_INTERVAL)) return;
        if ("downloading".equals(phase) || "verifying".equals(phase) || "ready".equals(phase) || !checking.compareAndSet(false, true)) return;
        worker.execute(() -> {
            phase = "checking"; error = ""; message = "正在检查更新…"; emit();
            prefs.edit().putLong("lastCheckedAt", System.currentTimeMillis()).apply();
            try {
                JSONArray releases = new JSONArray(get("https://api.github.com/repos/" + UpdatePolicy.REPOSITORY + "/releases?per_page=100", 4 * 1024 * 1024));
                JSONObject selected = null; long latest = -1;
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.getJSONObject(i);
                    long number = UpdatePolicy.buildNumber(release.optString("tag_name"));
                    if (release.optBoolean("draft") || release.optBoolean("prerelease") || number <= latest || !hasManifest(release)) continue;
                    selected = release; latest = number;
                }
                if (selected == null || 100000 + latest <= installedCode) {
                    phase = "current"; message = "已是最新版本"; emit(); return;
                }
                String tag = selected.getString("tag_name");
                JSONObject manifest = new JSONObject(get(UpdatePolicy.assetUrl(tag, "update.json"), 32768));
                if (!tag.equals(manifest.optString("tag"))) throw new IllegalArgumentException("更新版本信息不一致");
                validateManifest(manifest);
                JSONArray assets = selected.getJSONArray("assets"); boolean apkPresent = false;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if ("DazoMobile.apk".equals(asset.optString("name")) && "uploaded".equals(asset.optString("state"))
                            && manifest.getLong("size") == asset.optLong("size")
                            && manifest.getString("apkUrl").equals(asset.optString("browser_download_url"))) apkPresent = true;
                }
                if (!apkPresent) throw new IllegalArgumentException("新版安装包尚未发布完整，请稍后重试");
                if (manifest.getInt("minSdk") > Build.VERSION.SDK_INT) throw new IllegalArgumentException("新版暂不支持当前 Android 系统");
                if (candidate.optLong("versionCode") != manifest.getLong("versionCode")) discardDownload();
                candidate = manifest;
                prefs.edit().putString("candidate", manifest.toString()).commit();
                beginDownload();
            } catch (Exception e) { fail(e); }
            finally { checking.set(false); }
        });
    }
    private boolean hasManifest(JSONObject release) {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return false;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && "update.json".equals(asset.optString("name")) && "uploaded".equals(asset.optString("state"))) return true;
        }
        return false;
    }
    private void validateManifest(JSONObject value) throws Exception {
        if (value.getInt("schemaVersion") != 1) throw new IllegalArgumentException("暂不支持此更新格式");
        UpdatePolicy.validate(value.getString("packageName"), context.getPackageName(), value.getString("tag"),
                value.getString("apkUrl"), value.getLong("versionCode"), value.getLong("size"), value.getString("sha256"), value.getInt("minSdk"));
        String certificate = value.getString("certificateSha256");
        if (!certificate.matches("[a-fA-F0-9]{64}")) throw new IllegalArgumentException("更新签名信息无效");
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo self = context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
        Signature[] installed = Build.VERSION.SDK_INT >= 28 && self.signingInfo != null ? self.signingInfo.getApkContentsSigners() : self.signatures;
        boolean matches = false;
        if (installed != null) for (Signature signature : installed) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            StringBuilder hash = new StringBuilder();
            for (byte item : digest) hash.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
            if (hash.toString().equalsIgnoreCase(certificate)) matches = true;
        }
        if (!matches) throw new IllegalArgumentException("新版签名与当前安装来源不同，无法覆盖更新");
    }
    private File apkFile() { return new File(directory, "DazoMobile-" + candidate.optLong("versionCode") + ".apk"); }
    private File partialFile() { return new File(directory, apkFile().getName() + ".part"); }
    private void beginDownload() throws Exception {
        prefs.edit().putBoolean("active", true).commit();
        cancelled = false;
        if (!visible) { phase = "available"; message = "发现新版，返回应用后开始下载"; emit(); return; }
        downloaded = partialFile().length(); phase = "downloading"; error = ""; message = "正在下载新版…"; emit();
        try { ContextCompat.startForegroundService(context, new Intent(context, UpdateDownloadService.class)); }
        catch (RuntimeException e) { phase = "available"; message = "发现新版，请回到应用继续下载"; emit(); }
    }
    private void recover() throws Exception {
        if (candidate.optLong("versionCode") <= installedCode || transferring.get() || checking.get()) return;
        if (apkFile().isFile()) {
            verifyApk(); downloaded = apkFile().length(); phase = "ready"; error = ""; message = "新版已下载，可以安装"; emit();
        } else if (prefs.getBoolean("active", false) && !"error".equals(phase)) beginDownload();
    }
    /** Runs on the foreground service's worker, independent of the React Native lifecycle. */
    void transfer(Progress progress) {
        if (!prefs.getBoolean("active", false) || !transferring.compareAndSet(false, true)) return;
        try {
            validateManifest(candidate);
            File partial = partialFile();
            long total = candidate.getLong("size"), offset = partial.length();
            if (offset > total) { partial.delete(); offset = 0; }
            if (offset < total) {
                HttpURLConnection connection = openDownload(candidate.getString("apkUrl"), offset);
                try {
                    int status = connection.getResponseCode();
                    if (status == 200) offset = 0;
                    else if (status == 206) {
                        String range = connection.getHeaderField("Content-Range");
                        if (! ("bytes " + offset + "-" + (total - 1) + "/" + total).equals(range)) throw new IllegalArgumentException("续传范围不一致，请重新下载");
                    } else throw new java.io.IOException("HTTP " + status);
                    long length = connection.getContentLengthLong();
                    if (length >= 0 && length != total - offset) throw new IllegalArgumentException("更新包大小不一致");
                    downloaded = offset;
                    try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial, offset > 0)) {
                        byte[] buffer = new byte[65536]; int count; long emittedAt = 0;
                        while ((count = input.read(buffer)) != -1) {
                            if (cancelled || Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                            if (downloaded + count > total) throw new IllegalArgumentException("更新包大小不一致");
                            output.write(buffer, 0, count); downloaded += count;
                            if (System.currentTimeMillis() - emittedAt > 500) {
                                emittedAt = System.currentTimeMillis(); emit(); progress.changed(downloaded, total);
                            }
                        }
                        output.getFD().sync();
                    }
                } finally { connection.disconnect(); activeConnection = null; }
            }
            if (cancelled) throw new java.io.InterruptedIOException();
            if (partial.length() != total) throw new java.io.IOException("下载中断");
            if (!partial.renameTo(apkFile())) throw new java.io.IOException("无法保存更新包");
            phase = "verifying"; message = "正在校验安装包…"; emit();
            verifyApk();
            if (cancelled) throw new java.io.InterruptedIOException();
            downloaded = total; phase = "ready"; message = "新版已下载，可以安装"; error = "";
            prefs.edit().putBoolean("active", false).commit(); emit();
        } catch (Exception e) {
            if (cancelled) { discardDownload(); phase = "idle"; message = "已取消下载"; error = ""; emit(); }
            else { if (!(e instanceof java.io.IOException)) discardDownload(); fail(e); }
        } finally { activeConnection = null; transferring.set(false); }
    }
    private HttpURLConnection openDownload(String address, long offset) throws Exception {
        for (int redirects = 0; redirects < 6; redirects++) {
            if (cancelled) throw new java.io.InterruptedIOException();
            if (!UpdatePolicy.isHttps(address)) throw new IllegalArgumentException("更新连接必须使用 HTTPS");
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
            activeConnection = connection;
            connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "DazoMobile/" + installedName);
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400 && connection.getHeaderField("Location") != null) {
                    address = new URL(new URL(address), connection.getHeaderField("Location")).toString(); connection.disconnect(); continue;
                }
                return connection;
            } catch (Exception e) { connection.disconnect(); throw e; }
        }
        throw new IllegalArgumentException("更新连接重定向过多");
    }
    void interrupt() {
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
    }
    void cancel() {
        cancelled = true; interrupt();
        worker.execute(() -> {
            prefs.edit().putBoolean("active", false).commit();
            if (!transferring.get()) { discardDownload(); phase = "idle"; message = "已取消下载"; error = ""; emit(); }
        });
    }
    private void discardDownload() {
        downloaded = 0;
        prefs.edit().putBoolean("active", false).apply();
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) file.delete();
    }
    private void fail(Exception e) {
        phase = "error";
        error = e instanceof java.io.IOException ? "连接更新服务失败，请检查网络后重试；已下载部分会保留" : e.getMessage();
        message = error; emit();
    }
    private void verifyApk() throws Exception {
        File file = apkFile();
        if (!file.isFile() || file.length() != candidate.getLong("size")) throw new IllegalArgumentException("更新包下载不完整");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hash = new StringBuilder(); for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        if (!hash.toString().equalsIgnoreCase(candidate.getString("sha256"))) throw new IllegalArgumentException("更新包校验失败，请重新下载");
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageManager packages = context.getPackageManager();
        PackageInfo archive = packages.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        PackageInfo installed = packages.getPackageInfo(context.getPackageName(), flags);
        if (archive == null || archive.applicationInfo == null || archive.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT
                || !context.getPackageName().equals(archive.packageName) || versionCode(archive) != candidate.getLong("versionCode")
                || versionCode(archive) <= installedCode) throw new IllegalArgumentException("安装包版本不匹配，已停止更新");
        if (!signatures(archive).equals(signatures(installed)) || signatures(installed).isEmpty()) throw new IllegalArgumentException("安装包签名不匹配，已停止更新");
    }
    private static long versionCode(PackageInfo value) { return Build.VERSION.SDK_INT >= 28 ? value.getLongVersionCode() : value.versionCode; }
    private static Set<String> signatures(PackageInfo value) {
        Signature[] certificates = Build.VERSION.SDK_INT >= 28 && value.signingInfo != null ? value.signingInfo.getApkContentsSigners() : value.signatures;
        Set<String> result = new HashSet<>();
        if (certificates != null) for (Signature certificate : certificates) result.add(certificate.toCharsString());
        return result;
    }
    void install(Activity activity) {
        if (activity == null) throw new IllegalStateException("请回到应用后安装更新");
        if (!"ready".equals(phase)) throw new IllegalStateException("安装包尚未准备完成");
        worker.execute(() -> {
            try {
                verifyApk();
                activity.runOnUiThread(() -> {
                    try {
                        if (!context.getPackageManager().canRequestPackageInstalls()) {
                            awaitingPermission = true;
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.getPackageName())));
                            return;
                        }
                        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".updates", apkFile());
                        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setClipData(ClipData.newRawUri("DazoGallery update", uri));
                        activity.startActivity(intent);
                    } catch (Exception e) { error = "无法打开系统安装程序：" + e.getMessage(); emit(); }
                });
            } catch (Exception e) { fail(e); }
        });
    }
    void resumeInstallation(Activity activity) {
        if (awaitingPermission && activity != null) {
            awaitingPermission = false;
            if (context.getPackageManager().canRequestPackageInstalls()) install(activity);
            else { error = "允许安装此来源的应用后，可再次点击安装"; emit(); }
        }
    }
    private String get(String address, int limit) throws Exception {
        for (int redirects = 0; redirects < 6; redirects++) {
            if (!UpdatePolicy.isHttps(address)) throw new IllegalArgumentException("更新连接必须使用 HTTPS");
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "DazoMobile/" + installedName);
            connection.setRequestProperty("Accept", "application/vnd.github+json, application/json, */*");
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400 && connection.getHeaderField("Location") != null) {
                    address = new URL(new URL(address), connection.getHeaderField("Location")).toString(); continue;
                }
                if (status == 403 || status == 429) throw new IllegalStateException("GitHub 检查次数已达上限，请稍后重试");
                if (status != 200) throw new IllegalStateException("更新服务暂不可用（HTTP " + status + "）");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] bytes = new byte[8192]; int count;
                    while ((count = input.read(bytes)) != -1) {
                        if (output.size() + count > limit) throw new IllegalArgumentException("更新信息过大");
                        output.write(bytes, 0, count);
                    }
                    return output.toString("UTF-8");
                }
            } finally { connection.disconnect(); }
        }
        throw new IllegalStateException("更新连接重定向过多");
    }
}
