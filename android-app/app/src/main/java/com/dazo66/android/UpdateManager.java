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
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Uses public GitHub Releases and the system download/installation services, without account cookies. */
final class UpdateManager {
    interface Listener { void changed(String json); }
    private static final long CHECK_INTERVAL = 6L * 60 * 60 * 1000;
    private final Context context;
    private final SharedPreferences prefs;
    private final DownloadManager downloads;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean checking = new AtomicBoolean();
    private final long installedCode;
    private final String installedName;
    private final File directory;
    private final Listener listener;
    private volatile JSONObject candidate;
    private volatile String phase = "idle", message = "", error = "";
    private volatile long downloadId = -1, downloaded = 0;
    private volatile boolean visible, awaitingPermission;
    private ScheduledFuture<?> polling;

    UpdateManager(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        prefs = context.getSharedPreferences("app-updates", 0);
        downloads = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            installedCode = versionCode(info); installedName = info.versionName;
        } catch (Exception e) { throw new IllegalStateException(e); }
        directory = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates");
        directory.mkdirs();
        try { candidate = new JSONObject(prefs.getString("candidate", "{}")); } catch (Exception ignored) { candidate = new JSONObject(); }
        downloadId = prefs.getLong("downloadId", -1);
        worker.execute(() -> {
            try {
                if (candidate.optLong("versionCode") <= installedCode) { discardDownload(); candidate = new JSONObject(); prefs.edit().remove("candidate").apply(); }
                else validateManifest(candidate);
                reconcile();
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
    private void emit() { String value = state(); main.post(() -> listener.changed(value)); }
    void setAutoCheck(boolean value) { prefs.edit().putBoolean("autoCheck", value).apply(); emit(); }
    void setVisible(boolean value) {
        visible = value;
        worker.execute(() -> {
            if (value) { try { reconcile(); } catch (Exception e) { fail(e); } }
            else stopPolling();
        });
    }
    void check(boolean manual) {
        if (!manual && (!prefs.getBoolean("autoCheck", true) || System.currentTimeMillis() - prefs.getLong("lastCheckedAt", 0) < CHECK_INTERVAL)) return;
        if (downloadId >= 0 || "ready".equals(phase) || !checking.compareAndSet(false, true)) return;
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
                candidate = manifest;
                prefs.edit().putString("candidate", manifest.toString()).apply();
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
    private void beginDownload() throws Exception {
        discardDownload();
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(candidate.getString("apkUrl")));
        request.setTitle("Dazo " + candidate.getString("versionName"));
        request.setDescription("正在下载应用更新");
        request.setMimeType("application/vnd.android.package-archive");
        request.addRequestHeader("User-Agent", "DazoMobile/" + installedName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/" + apkFile().getName());
        downloadId = downloads.enqueue(request);
        prefs.edit().putLong("downloadId", downloadId).apply();
        downloaded = 0; phase = "downloading"; error = ""; message = "正在下载新版…"; emit(); startPolling();
    }
    private void reconcile() throws Exception {
        if (downloadId < 0) return;
        try (Cursor cursor = downloads.query(new DownloadManager.Query().setFilterById(downloadId))) {
            if (cursor == null || !cursor.moveToFirst()) throw new IllegalStateException("更新下载已被移除，请重新检查");
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            if (downloaded > candidate.optLong("size") || downloaded > UpdatePolicy.MAX_APK_BYTES) throw new IllegalArgumentException("更新包大小不一致");
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                if (!"ready".equals(phase)) {
                    phase = "verifying"; message = "正在校验安装包…"; emit();
                    verifyApk();
                    phase = "ready"; message = "新版已下载，可以安装"; error = ""; emit();
                }
                stopPolling();
            } else if (status == DownloadManager.STATUS_FAILED) {
                throw new IllegalStateException("更新下载失败，请检查网络或存储空间后重试");
            } else {
                phase = "downloading";
                message = status == DownloadManager.STATUS_PAUSED ? "等待网络连接…" : status == DownloadManager.STATUS_PENDING ? "等待开始下载…" : "正在下载新版…";
                emit(); startPolling();
            }
        }
    }
    private void startPolling() {
        if (visible && (polling == null || polling.isDone())) polling = worker.scheduleWithFixedDelay(() -> {
            try { reconcile(); } catch (Exception e) { fail(e); }
        }, 1, 1, TimeUnit.SECONDS);
    }
    private void stopPolling() { if (polling != null) polling.cancel(false); polling = null; }
    void cancel() { worker.execute(() -> { discardDownload(); phase = "idle"; message = "已取消下载"; error = ""; emit(); }); }
    private void discardDownload() {
        stopPolling();
        if (downloadId >= 0) downloads.remove(downloadId);
        downloadId = -1; downloaded = 0;
        prefs.edit().remove("downloadId").apply();
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) file.delete();
    }
    private void fail(Exception e) {
        if (downloadId >= 0) discardDownload();
        phase = "error";
        error = e instanceof java.io.IOException ? "连接更新服务失败，请检查网络后重试" : e.getMessage();
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
                        intent.setClipData(ClipData.newRawUri("Dazo update", uri));
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
