package com.dazo66.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** One native queue survives navigation and never changes the selected destination or account. */
final class FileUploads {
    interface Listener { void changed(String json); }
    interface Progress { void changed(String name, long bytes, long total); }
    private static FileUploads instance;
    static synchronized FileUploads get(Context context) { if (instance == null) instance = new FileUploads(context.getApplicationContext()); return instance; }
    private final Context context;
    private final ApiClient api;
    private final SharedPreferences prefs;
    private final ExecutorService preparation = Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> jobs = new ArrayList<>();
    private volatile Listener listener;
    private volatile UploadControl active;
    private volatile String activeId;
    private FileUploads(Context context) {
        this.context = context; api = ApiClient.get(context); prefs = context.getSharedPreferences("file-uploads", 0);
        try {
            JSONArray saved = new JSONArray(prefs.getString("jobs", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject job = saved.getJSONObject(i);
                put(job, "stopping", false);
                if ("uploading".equals(job.optString("phase")) || "preparing".equals(job.optString("phase"))) {
                    put(job, "phase", "error"); put(job, "error", "上传已中断，可重试");
                }
                jobs.add(job);
            }
        } catch (Exception ignored) { jobs.clear(); }
        persist();
    }
    private static void put(JSONObject job, String key, Object value) { try { job.put(key, value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static boolean pending(JSONObject job) { String phase = job.optString("phase"); return "preparing".equals(phase) || "queued".equals(phase) || "uploading".equals(phase); }
    synchronized String state() {
        JSONArray values = new JSONArray();
        for (JSONObject job : jobs) if (api.scope().equals(job.optString("scope"))) values.put(job);
        return values.toString();
    }
    void setListener(Listener value) { listener = value; emit(); }
    private void emit() { Listener value = listener; if (value != null) try { value.changed(state()); } catch (RuntimeException ignored) {} }
    private synchronized void persist() { prefs.edit().putString("jobs", new JSONArray(jobs).toString()).apply(); }
    synchronized String enqueue(List<Uri> sources, String folder, String ownerScope) {
        if (!api.uploadReady(ownerScope)) throw new IllegalStateException("请先登录对应服务器");
        long count = jobs.stream().filter(FileUploads::pending).count();
        if (sources.size() > 100 || count + sources.size() > 100) throw new IllegalArgumentException("最多同时排队100个文件");
        ArrayList<JSONObject> added = new ArrayList<>();
        for (Uri uri : sources) {
            boolean duplicate = false;
            for (JSONObject job : jobs) if (pending(job) && ownerScope.equals(job.optString("scope")) && folder.equals(job.optString("folder")) && uri.toString().equals(job.optString("uri"))) duplicate = true;
            if (duplicate) continue;
            JSONObject job = new JSONObject();
            put(job, "id", UUID.randomUUID().toString()); put(job, "scope", ownerScope); put(job, "folder", folder);
            put(job, "uri", uri.toString()); put(job, "name", "正在读取文件…"); put(job, "phase", "preparing");
            put(job, "bytes", 0); put(job, "total", 0); put(job, "dismissed", false);
            jobs.add(job); added.add(job);
        }
        while (jobs.size() > 150) {
            JSONObject oldest = null; for (JSONObject job : jobs) if (!pending(job)) { oldest = job; break; }
            if (oldest == null) break;
            jobs.remove(oldest); releaseIfUnused(oldest.optString("uri"));
        }
        persist(); emit();
        preparation.execute(() -> { for (JSONObject job : added) prepare(job); resume(); });
        resume();
        JSONArray ids = new JSONArray(); for (JSONObject job : added) ids.put(job.optString("id")); return ids.toString();
    }
    private void prepare(JSONObject job) {
        synchronized (this) { if (!"preparing".equals(job.optString("phase"))) return; }
        Uri uri = Uri.parse(job.optString("uri"));
        try {
            try { context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
            String name = uri.getLastPathSegment(); long size = -1;
            try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME), s = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (n >= 0 && !cursor.isNull(n)) name = cursor.getString(n);
                    if (s >= 0 && !cursor.isNull(s)) size = cursor.getLong(s);
                }
            }
            if (size < 0) try (android.content.res.AssetFileDescriptor file = context.getContentResolver().openAssetFileDescriptor(uri, "r")) { if (file != null) size = file.getLength(); }
            synchronized (this) {
                if (!"preparing".equals(job.optString("phase"))) { releaseIfUnused(uri.toString()); return; }
                put(job, "name", name == null || name.isEmpty() ? "未命名文件" : name); put(job, "total", Math.max(0, size));
                if (size < 1 || size > UploadTransfer.MAX_BYTES) throw new IllegalArgumentException(size < 0 ? "无法读取文件大小，请先下载到本机" : "文件大小必须在1字节到2GB之间");
                if (!api.uploadReady(job.optString("scope"))) throw new IllegalStateException("登录账户已变化，请重新选择文件");
                put(job, "phase", "queued"); persist(); emit();
            }
        } catch (Exception error) {
            synchronized (this) {
                if ("preparing".equals(job.optString("phase"))) { put(job, "phase", "error"); put(job, "error", message(error)); persist(); emit(); }
            }
        }
    }
    synchronized void resume() {
        if (!api.uploadReady(api.scope()) || !hasWork()) return;
        try { ContextCompat.startForegroundService(context, new Intent(context, FileUploadService.class)); }
        catch (RuntimeException ignored) { /* The next foreground resume starts jobs if Android currently disallows FGS. */ }
    }
    synchronized boolean hasQueued() {
        if (!api.uploadReady(api.scope())) return false;
        for (JSONObject job : jobs) if ("queued".equals(job.optString("phase")) && api.scope().equals(job.optString("scope"))) return true;
        return false;
    }
    synchronized boolean hasWork() {
        if (!api.uploadReady(api.scope())) return false;
        for (JSONObject job : jobs) if (pending(job) && api.scope().equals(job.optString("scope"))) return true;
        return false;
    }
    private synchronized JSONObject take() {
        if (!api.uploadReady(api.scope())) return null;
        for (JSONObject job : jobs) if ("queued".equals(job.optString("phase")) && api.scope().equals(job.optString("scope"))) {
            put(job, "phase", "uploading"); persist(); emit(); return job;
        }
        return null;
    }
    void runQueue(Progress progress) {
        while (!Thread.currentThread().isInterrupted()) {
            JSONObject job = take();
            if (job != null) transfer(job, progress);
            else if (hasWork()) {
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            } else return;
        }
    }
    private void transfer(JSONObject job, Progress progress) {
        UploadControl control = new UploadControl();
        synchronized (this) {
            if (!"uploading".equals(job.optString("phase"))) return;
            activeId = job.optString("id"); active = control;
        }
        final long[] emitted = {0}, persisted = {0};
        try {
            api.uploadFile(context, Uri.parse(job.getString("uri")), job.getString("name"), job.getLong("total"), job.getString("folder"), job.getString("scope"), control, bytes -> {
                synchronized (this) {
                    put(job, "bytes", bytes);
                    long now = System.currentTimeMillis();
                    if (now - emitted[0] >= 250) { emitted[0] = now; emit(); progress.changed(job.optString("name"), bytes, job.optLong("total")); }
                    if (now - persisted[0] >= 1000) { persisted[0] = now; persist(); }
                }
            });
            synchronized (this) { put(job, "phase", "complete"); put(job, "bytes", job.optLong("total")); put(job, "error", ""); }
        } catch (Exception error) {
            synchronized (this) {
                if (!"cancelled".equals(job.optString("phase"))) { put(job, "phase", "error"); put(job, "error", control.stopped ? "上传已中断，可重试" : message(error)); }
                if (!api.uploadReady(job.optString("scope"))) for (JSONObject waiting : jobs)
                    if (pending(waiting) && job.optString("scope").equals(waiting.optString("scope"))) { put(waiting, "phase", "error"); put(waiting, "error", "登录已变化，请重新登录后重试"); }
            }
        } finally {
            synchronized (this) { put(job, "stopping", false); if (active == control) { active = null; activeId = null; } releaseIfUnused(job.optString("uri")); persist(); emit(); }
        }
    }
    void cancel(String id) {
        UploadControl stop;
        synchronized (this) {
            stop = id.equals(activeId) ? active : null;
            for (JSONObject job : jobs) if (id.equals(job.optString("id")) && api.scope().equals(job.optString("scope")) && pending(job)) {
                put(job, "phase", "cancelled"); put(job, "stopping", stop != null);
            }
            persist(); emit();
        }
        if (stop != null) stop.stop();
    }
    void cancelAll() {
        UploadControl stop;
        synchronized (this) {
            for (JSONObject job : jobs) if (pending(job)) { put(job, "phase", "cancelled"); put(job, "dismissed", true); }
            stop = active; persist(); emit();
        }
        if (stop != null) stop.stop();
    }
    synchronized void retry(String id) {
        if (!api.uploadReady(api.scope())) throw new IllegalStateException("请先登录服务器");
        if (id.equals(activeId)) throw new IllegalStateException("正在结束上传，请稍后重试");
        for (JSONObject job : jobs) if (id.equals(job.optString("id")) && api.scope().equals(job.optString("scope")) && ("error".equals(job.optString("phase")) || "cancelled".equals(job.optString("phase")))) {
            put(job, "phase", "preparing"); put(job, "dismissed", false); put(job, "bytes", 0); put(job, "error", ""); persist(); emit();
            preparation.execute(() -> { prepare(job); resume(); }); return;
        }
    }
    synchronized void dismiss() {
        for (JSONObject job : jobs) if (!pending(job) && api.scope().equals(job.optString("scope"))) put(job, "dismissed", true);
        for (JSONObject job : jobs) if (!pending(job) && api.scope().equals(job.optString("scope"))) releaseIfUnused(job.optString("uri"));
        persist(); emit();
    }
    void interrupt() { UploadControl control = active; if (control != null) control.stop(); }
    private synchronized void releaseIfUnused(String source) {
        for (JSONObject job : jobs) if (source.equals(job.optString("uri")) && (pending(job) || "error".equals(job.optString("phase")) || ("cancelled".equals(job.optString("phase")) && !job.optBoolean("dismissed")))) return;
        try { context.getContentResolver().releasePersistableUriPermission(Uri.parse(source), Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
    }
    private static String message(Exception error) { return error.getMessage() == null ? "上传失败，请重试" : error.getMessage(); }
}
