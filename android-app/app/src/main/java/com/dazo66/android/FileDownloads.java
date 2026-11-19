package com.dazo66.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** User-owned downloads are saved in public Downloads/Dazo, separately from the bounded image cache. */
final class FileDownloads {
    interface Listener { void changed(String json); }
    interface Progress { void changed(String name, long bytes, long total); }
    private static FileDownloads instance;
    static synchronized FileDownloads get(Context context) { if (instance == null) instance = new FileDownloads(context.getApplicationContext()); return instance; }
    private final Context context;
    private final ApiClient api;
    private final SharedPreferences prefs;
    private final ArrayList<JSONObject> jobs = new ArrayList<>();
    private volatile Listener listener;
    private volatile ApiClient.HttpStream active;
    private volatile String activeId;
    private FileDownloads(Context context) {
        this.context = context; api = ApiClient.get(context); prefs = context.getSharedPreferences("file-downloads", 0);
        try {
            JSONArray saved = new JSONArray(prefs.getString("jobs", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject job = saved.getJSONObject(i); jobs.add(job);
                if ("downloading".equals(job.optString("phase"))) {
                    cleanup(job); put(job, "phase", "error"); put(job, "error", "下载已中断，可重新下载");
                }
            }
            persist();
        } catch (Exception ignored) { jobs.clear(); }
    }
    private static void put(JSONObject job, String key, Object value) { try { job.put(key, value); } catch (Exception e) { throw new IllegalStateException(e); } }
    synchronized String state() {
        JSONArray values = new JSONArray();
        for (JSONObject job : jobs) if (api.scope().equals(job.optString("scope"))) values.put(job);
        return values.toString();
    }
    void setListener(Listener value) { listener = value; emit(); }
    private void emit() { Listener value = listener; if (value != null) value.changed(state()); }
    private synchronized void persist() { prefs.edit().putString("jobs", new JSONArray(jobs).toString()).apply(); }
    synchronized String enqueue(String path, String filename) {
        if (!api.signedIn() || !api.trusted(path)) throw new IllegalArgumentException("请先登录对应的服务器");
        for (JSONObject job : jobs) if (api.scope().equals(job.optString("scope")) && path.equals(job.optString("path")) && ("queued".equals(job.optString("phase")) || "downloading".equals(job.optString("phase")))) return job.toString();
        JSONObject job = new JSONObject();
        put(job, "id", java.util.UUID.randomUUID().toString()); put(job, "scope", api.scope()); put(job, "path", path);
        put(job, "name", safeName(filename)); put(job, "phase", "queued"); put(job, "bytes", 0); put(job, "total", 0); put(job, "seen", false); put(job, "location", "下载 / Dazo");
        jobs.add(0, job);
        while (jobs.size() > 30) {
            JSONObject oldest = jobs.get(jobs.size()-1);
            if ("queued".equals(oldest.optString("phase")) || "downloading".equals(oldest.optString("phase"))) break;
            jobs.remove(jobs.size()-1);
        }
        persist(); emit(); start(); return job.toString();
    }
    private void start() { ContextCompat.startForegroundService(context, new Intent(context, FileDownloadService.class)); }
    synchronized void resume() { if (hasQueued()) start(); }
    synchronized boolean hasQueued() { for (JSONObject job : jobs) if ("queued".equals(job.optString("phase"))) return true; return false; }
    private synchronized JSONObject take() {
        for (JSONObject job : jobs) if ("queued".equals(job.optString("phase"))) { put(job, "phase", "downloading"); persist(); emit(); return job; }
        return null;
    }
    void runQueue(Progress progress) { JSONObject job; while ((job = take()) != null) transfer(job, progress); }
    private void transfer(JSONObject job, Progress progress) {
        activeId = job.optString("id"); Uri uri = null;
        try {
            if (!api.scope().equals(job.optString("scope")) || !api.signedIn()) throw new IllegalStateException("登录账户已变化，请重新下载");
            String name = job.getString("name"), mime = mime(name);
            OutputStream output;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues(); values.put(MediaStore.Downloads.DISPLAY_NAME, name); values.put(MediaStore.Downloads.MIME_TYPE, mime);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Dazo"); values.put(MediaStore.Downloads.IS_PENDING, 1);
                uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new java.io.IOException("无法创建下载文件");
                output = context.getContentResolver().openOutputStream(uri);
            } else {
                File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Dazo");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("无法创建下载目录");
                File file = new File(directory, name);
                if (file.exists()) file = new File(directory, System.currentTimeMillis() + "-" + name);
                put(job, "file", file.getAbsolutePath()); uri = FileProvider.getUriForFile(context, context.getPackageName() + ".updates", file); output = new FileOutputStream(file);
            }
            synchronized (this) { put(job, "uri", uri.toString()); put(job, "mime", mime); persist(); }
            if (output == null) throw new java.io.IOException("无法写入下载文件");
            try (OutputStream target = output) {
                byte[] buffer = new byte[65536]; long bytes = 0, emittedAt = 0, total = -1; String version = null;
                for (int retry = 0; ; retry++) {
                    ApiClient.HttpStream current = null;
                    try (ApiClient.HttpStream stream = api.openStream(job.getString("path"), bytes, -1)) {
                        active = stream; current = stream;
                        String stamp = stream.connection.getHeaderField("ETag");
                        if (stamp == null) stamp = stream.connection.getHeaderField("Last-Modified");
                        if (retry > 0 && version != null && stamp != null && !version.equals(stamp)) throw new IllegalStateException("服务器文件已变化，请重新下载");
                        version = stamp;
                        long available = stream.length < 0 ? -1 : bytes + stream.length;
                        if (total >= 0 && available >= 0 && total != available) throw new IllegalStateException("服务器文件大小已变化，请重新下载");
                        total = available; synchronized (this) { put(job, "total", total); }
                        int count;
                        while ((count = stream.input.read(buffer)) != -1) {
                            if ("cancelled".equals(job.optString("phase"))) throw new java.io.InterruptedIOException();
                            if (!api.scope().equals(job.optString("scope")) || !api.signedIn()) throw new IllegalStateException("登录账户已变化，请重新下载");
                            try { target.write(buffer, 0, count); } catch (java.io.IOException error) { throw new IllegalStateException("存储空间不足或无法写入文件", error); }
                            bytes += count; synchronized (this) { put(job, "bytes", bytes); }
                            if (System.currentTimeMillis() - emittedAt > 500) { emittedAt = System.currentTimeMillis(); persist(); emit(); progress.changed(name, bytes, total); }
                        }
                        if (total >= 0 && bytes != total) throw new java.io.IOException("下载文件不完整");
                        break;
                    } catch (java.io.IOException failure) {
                        if ("cancelled".equals(job.optString("phase")) || retry >= 3) throw failure;
                        api.streamFailed(current);
                    } finally { active = null; }
                }
                target.flush();
            }
            if ("cancelled".equals(job.optString("phase"))) throw new java.io.InterruptedIOException();
            if (Build.VERSION.SDK_INT >= 29) { ContentValues values = new ContentValues(); values.put(MediaStore.Downloads.IS_PENDING, 0); context.getContentResolver().update(uri, values, null, null); }
            synchronized (this) { put(job, "phase", "complete"); put(job, "finishedAt", System.currentTimeMillis()); persist(); }
        } catch (Exception error) {
            cleanup(job);
            synchronized (this) {
                if (!"cancelled".equals(job.optString("phase"))) { put(job, "phase", "error"); put(job, "error", error instanceof java.io.IOException ? "下载中断，请检查网络或存储空间后重试" : error.getMessage()); }
                persist();
            }
        } finally { active = null; activeId = null; emit(); }
    }
    private void cleanup(JSONObject job) {
        try {
            if (Build.VERSION.SDK_INT >= 29 && !job.optString("uri").isEmpty()) context.getContentResolver().delete(Uri.parse(job.optString("uri")), null, null);
            else if (!job.optString("file").isEmpty()) new File(job.getString("file")).delete();
        } catch (Exception ignored) {}
    }
    synchronized void markSeen(String id) { for (JSONObject job : jobs) if (id.equals(job.optString("id"))) put(job, "seen", true); persist(); }
    synchronized void cancel(String id) {
        for (JSONObject job : jobs) if (id.equals(job.optString("id")) && ("queued".equals(job.optString("phase")) || "downloading".equals(job.optString("phase")))) put(job, "phase", "cancelled");
        if (id.equals(activeId) && active != null) active.connection.disconnect(); persist(); emit();
    }
    void interrupt() { ApiClient.HttpStream stream = active; if (stream != null) stream.connection.disconnect(); }
    synchronized void open(Activity activity, String id) {
        for (JSONObject job : jobs) if (id.equals(job.optString("id")) && "complete".equals(job.optString("phase"))) {
            Intent intent = viewIntent(job); activity.startActivity(intent); return;
        }
        throw new IllegalArgumentException("文件尚未下载完成");
    }
    static Intent viewIntent(JSONObject job) {
        Uri uri = Uri.parse(job.optString("uri"));
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, job.optString("mime", "application/octet-stream")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("download", uri)); return intent;
    }
    static String safeName(String input) {
        String name = input.replaceAll("[/\\\\\r\n]", "_").replaceAll("^\\.+", "").trim();
        if (name.isEmpty()) name = "download";
        if (name.length() > 180) name = name.substring(0, 160) + name.substring(name.length()-15);
        return name;
    }
    static String mime(String name) { int dot = name.lastIndexOf('.'); String type = dot < 0 ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot+1).toLowerCase(java.util.Locale.ROOT)); return type == null ? "application/octet-stream" : type; }
}
