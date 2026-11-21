package com.dazo66.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.PermissionAwareActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.json.JSONArray;

/** Android facilities only; all screens and navigation are implemented in React Native. */
public final class DazoBridge extends ReactContextBaseJavaModule {
    private final ApiClient api;
    private final UpdateManager updates;
    private final FileDownloads downloads;
    private Promise picker;
    private String uploadFolder;
    private AtomicBoolean upload;
    private static final int PICK_FILE = 101, PICK_AVATAR = 102;

    DazoBridge(ReactApplicationContext context, ApiClient api) {
        super(context); this.api = api;
        downloads = FileDownloads.get(context);
        downloads.setListener(json -> {
            if (context.hasActiveReactInstance()) context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("DazoDownloadState", json);
        });
        api.setConnectionListener((json, error) -> {
            if (context.hasActiveReactInstance()) context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("DazoConnectionState", json);
        });
        updates = UpdateManager.get(context);
        updates.setListener(json -> {
            if (context.hasActiveReactInstance()) context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("DazoUpdateState", json);
        });
        context.addLifecycleEventListener(new LifecycleEventListener() {
            @Override public void onHostResume() { updates.setVisible(true); updates.resumeInstallation(context.getCurrentActivity()); try { downloads.resume(); } catch (RuntimeException ignored) {} }
            @Override public void onHostPause() { updates.setVisible(false); }
            @Override public void onHostDestroy() { updates.setVisible(false); }
        });
        context.addActivityEventListener(new BaseActivityEventListener() {
            @Override public void onActivityResult(Activity activity, int code, int result, Intent data) {
                if (code != PICK_FILE && code != PICK_AVATAR) return;
                Promise pending = picker; picker = null; if (pending == null) return;
                if (result != Activity.RESULT_OK || data == null || data.getData() == null) { pending.reject("CANCELLED", "已取消"); return; }
                Uri uri = data.getData();
                if (code == PICK_AVATAR) { cropAvatar(activity, uri, pending); return; }
                String name = "upload"; long size = -1;
                try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME), z = cursor.getColumnIndex(OpenableColumns.SIZE);
                        if (n >= 0) name = cursor.getString(n); if (z >= 0) size = cursor.getLong(z);
                    }
                }
                if (size < 1) { pending.reject("FILE", "无法读取文件大小"); return; }
                upload = api.upload(context, uri, name, size, uploadFolder, percent -> {
                    WritableMap event = Arguments.createMap(); event.putInt("percent", percent);
                    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("DazoUploadProgress", event);
                }, (value, error) -> { upload = null; complete(pending, value, error); });
            }
        });
    }
    @Override public String getName() { return "DazoBridge"; }
    private void complete(Promise promise, Object value, Exception error) {
        if (error != null) {
            String message = error instanceof ServerRoutes.Failure ? error.getMessage() : error instanceof java.net.SocketTimeoutException ? "连接超时，请稍后重试"
                    : error instanceof java.io.IOException ? "网络连接中断，请刷新确认结果后重试" : error.getMessage();
            promise.reject("上传已取消".equals(error.getMessage()) ? "CANCELLED" : api.signedIn() ? "REQUEST" : "SESSION", message, error);
        }
        else promise.resolve(value == null || value == JSONObject.NULL ? "null" : value.toString());
    }
    private JSONObject boot(JSONObject user) throws Exception {
        return new JSONObject().put("host", api.host()).put("activeHost", api.activeHost()).put("backupUrls", api.backupUrls()).put("userId", api.lastUser()).put("me", user == null ? JSONObject.NULL : user)
                .put("scope", api.scope()).put("state", getReactApplicationContext().getSharedPreferences("rn-state", 0).getString(api.scope(), "{}"));
    }
    @ReactMethod public void bootstrap(Promise promise) {
        if (!api.signedIn()) { try { promise.resolve(boot(null).toString()); } catch (Exception e) { promise.reject("BOOT", e); } return; }
        api.restore((user, error) -> {
            if (error != null && api.signedIn()) { promise.reject("BOOT", error); return; }
            try { promise.resolve(boot(error == null ? user : null).toString()); } catch (Exception e) { promise.reject("BOOT", e); }
        });
    }
    @ReactMethod public void connectionInfo(Promise promise) {
        try { promise.resolve(boot(null).toString()); } catch (Exception e) { promise.reject("BOOT", e); }
    }
    @ReactMethod public void connectionState(Promise promise) { promise.resolve(api.connectionState()); }
    @ReactMethod public void updateState(Promise promise) { promise.resolve(updates.state()); }
    @ReactMethod public void checkUpdate(boolean manual) { updates.check(manual); }
    @ReactMethod public void setAutoUpdate(boolean value) { updates.setAutoCheck(value); }
    @ReactMethod public void cancelUpdate() { updates.cancel(); }
    @ReactMethod public void installUpdate(Promise promise) {
        try { updates.install(getReactApplicationContext().getCurrentActivity()); promise.resolve(true); }
        catch (Exception e) { promise.reject("UPDATE", e.getMessage(), e); }
    }
    @ReactMethod public void login(String host, String backups, String user, String password, Promise promise) {
        api.login(host, backups, user, password, (me, error) -> { if (error != null) complete(promise, null, error); else try { updates.sessionChanged(); promise.resolve(boot(me).toString()); } catch (Exception e) { promise.reject("LOGIN", e); } });
    }
    @ReactMethod public void setBackupUrls(String value, Promise promise) {
        try { api.setBackupUrls(new JSONArray(value)); promise.resolve(api.backupUrls().toString()); }
        catch (Exception e) { promise.reject("HOST", e.getMessage(), e); }
    }
    @ReactMethod public void themeMode(Promise promise) {
        promise.resolve(getReactApplicationContext().getSharedPreferences("preferences", 0).getString("theme", "system"));
    }
    @ReactMethod public void setThemeMode(String value) {
        if (!("system".equals(value) || "dark".equals(value) || "light".equals(value))) return;
        getReactApplicationContext().getSharedPreferences("preferences", 0).edit().putString("theme", value).apply();
        getReactApplicationContext().runOnUiQueueThread(() -> MainApplication.applyTheme(getReactApplicationContext(), value));
    }
    @ReactMethod public void request(String method, String path, String body, Promise promise) {
        if (!path.startsWith("/api/")) { promise.reject("URL", "接口路径无效"); return; }
        try { api.request(method, path, body.isEmpty() ? null : new JSONObject(body), (value, error) -> complete(promise, value, error)); }
        catch (Exception e) { promise.reject("JSON", e); }
    }
    @ReactMethod public void media(String path, boolean force, Promise promise) { api.media(path, force, (value, error) -> complete(promise, value, error)); }
    @ReactMethod public void saveState(String scope, String value) { getReactApplicationContext().getSharedPreferences("rn-state", 0).edit().putString(scope, value).apply(); }
    @ReactMethod public void logout(Promise promise) { api.logout((x,e) -> { updates.sessionChanged(); promise.resolve(true); }); }
    @ReactMethod public void cacheSize(Promise promise) { promise.resolve((double) api.cacheBytes()); }
    @ReactMethod public void pickDateTime(String initial, boolean end, Promise promise) {
        Activity activity = getReactApplicationContext().getCurrentActivity();
        if (activity == null) { promise.reject("ACTIVITY", "页面不可用"); return; }
        activity.runOnUiThread(() -> {
            java.util.Calendar date = java.util.Calendar.getInstance();
            try { if (!initial.isEmpty()) date.setTime(new java.text.SimpleDateFormat(initial.length()>10?"yyyy-MM-dd HH:mm:ss":"yyyy-MM-dd", java.util.Locale.ROOT).parse(initial)); } catch (Exception ignored) {}
            android.app.DatePickerDialog dialog = new android.app.DatePickerDialog(activity, (view,y,m,d) -> {
                android.app.TimePickerDialog time = new android.app.TimePickerDialog(activity, (clock,hour,minute) -> promise.resolve(String.format(java.util.Locale.ROOT,"%04d-%02d-%02d %02d:%02d:%02d",y,m+1,d,hour,minute,end?59:0)), initial.isEmpty()?(end?23:0):date.get(java.util.Calendar.HOUR_OF_DAY),initial.isEmpty()?(end?59:0):date.get(java.util.Calendar.MINUTE),true);
                time.setOnCancelListener(x -> promise.reject("CANCELLED", "已取消")); time.show();
            },date.get(java.util.Calendar.YEAR),date.get(java.util.Calendar.MONTH),date.get(java.util.Calendar.DAY_OF_MONTH));
            dialog.setOnCancelListener(x -> promise.reject("CANCELLED", "已取消")); dialog.show();
        });
    }
    @ReactMethod public void clearCache(Promise promise) { api.clearCache((x,e) -> complete(promise, x, e)); }
    @ReactMethod public void cancelUpload() { if (upload != null) upload.set(true); }
    @ReactMethod public void addListener(String eventName) {}
    @ReactMethod public void removeListeners(double count) {}
    @ReactMethod public void pickUpload(String folder, Promise promise) { uploadFolder = folder; pick("*/*", PICK_FILE, promise); }
    @ReactMethod public void pickAvatar(Promise promise) { pick("image/*", PICK_AVATAR, promise); }
    private void pick(String type, int request, Promise promise) {
        Activity activity = getReactApplicationContext().getCurrentActivity();
        if (activity == null || picker != null || upload != null) { promise.reject("BUSY", "请先完成当前操作"); return; }
        picker = promise;
        activity.runOnUiThread(() -> {
            try { Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE); activity.startActivityForResult(intent, request); }
            catch (Exception e) { picker = null; promise.reject("PICKER", e); }
        });
    }
    @ReactMethod public void download(String path, String filename, Promise promise) {
        Activity activity = getReactApplicationContext().getCurrentActivity();
        if (activity == null) { promise.reject("DOWNLOAD", "请回到应用后下载"); return; }
        activity.runOnUiThread(() -> requestDownloadPermission(activity, path, filename, promise));
    }
    private void requestDownloadPermission(Activity activity, String path, String filename, Promise promise) {
        if (android.os.Build.VERSION.SDK_INT <= 28 && androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ((PermissionAwareActivity)activity).requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 104, (code, permissions, results) -> {
                if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) enqueueDownload(path, filename, promise);
                else promise.reject("DOWNLOAD", "允许存储权限后才能保存到下载目录");
                return true;
            }); return;
        }
        android.content.SharedPreferences prefs = activity.getSharedPreferences("preferences", 0);
        if (android.os.Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("downloadNotificationAsked", false) && androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("downloadNotificationAsked", true).apply();
            ((PermissionAwareActivity)activity).requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 105, (code, permissions, results) -> { enqueueDownload(path, filename, promise); return true; }); return;
        }
        enqueueDownload(path, filename, promise);
    }
    private void enqueueDownload(String path, String filename, Promise promise) {
        try { promise.resolve(downloads.enqueue(path, filename)); } catch (Exception error) { promise.reject("DOWNLOAD", error.getMessage(), error); }
    }
    @ReactMethod public void downloadsState(Promise promise) { promise.resolve(downloads.state()); }
    @ReactMethod public void acknowledgeDownload(String id) { downloads.markSeen(id); }
    @ReactMethod public void cancelDownload(String id) { downloads.cancel(id); }
    @ReactMethod public void openDownload(String id, Promise promise) {
        Activity activity = getReactApplicationContext().getCurrentActivity();
        if (activity == null) { promise.reject("DOWNLOAD", "请回到应用后打开"); return; }
        activity.runOnUiThread(() -> { try { downloads.open(activity, id); promise.resolve(true); } catch (Exception error) { promise.reject("DOWNLOAD", "没有可打开此文件的应用，可在文件管理器的系统下载目录查看", error); } });
    }
    @ReactMethod public void openDownloads(Promise promise) {
        Activity activity = getReactApplicationContext().getCurrentActivity();
        if (activity == null) { promise.reject("DOWNLOAD", "请回到应用后打开"); return; }
        activity.runOnUiThread(() -> { try { activity.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); promise.resolve(true); } catch (Exception error) { promise.reject("DOWNLOAD", "请在文件管理器中打开系统下载目录", error); } });
    }
    private void cropAvatar(Activity activity, Uri uri, Promise promise) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            try (InputStream input = activity.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(input, null, options); }
            if (options.outWidth < 1 || options.outHeight < 1 || (long) options.outWidth * options.outHeight > 16_000_000) throw new IllegalArgumentException("图片尺寸无效或超过 1600 万像素");
            Bitmap bitmap;
            try (InputStream input = activity.getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(input); }
            if (bitmap == null) throw new IllegalArgumentException("图片无效");
            AvatarCropView crop = new AvatarCropView(activity, bitmap);
            int size = Math.min(activity.getResources().getDisplayMetrics().widthPixels - 120, (int)(320 * activity.getResources().getDisplayMetrics().density));
            android.widget.FrameLayout frame = new android.widget.FrameLayout(activity); frame.addView(crop, new android.widget.FrameLayout.LayoutParams(size, size, android.view.Gravity.CENTER));
            AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("裁剪头像").setView(frame).setNegativeButton("取消", (a,b) -> promise.reject("CANCELLED", "已取消"))
                    .setPositiveButton("保存", (a,b) -> {
                        try {
                            File file = new File(activity.getCacheDir(), "avatar-" + System.nanoTime() + ".png");
                            try (FileOutputStream stream = new FileOutputStream(file)) { crop.export().compress(Bitmap.CompressFormat.PNG, 100, stream); }
                            api.uploadAvatar(activity, Uri.fromFile(file), (x,e) -> { file.delete(); complete(promise, x, e); });
                        } catch (Exception e) { promise.reject("AVATAR", e); }
                    }).create();
            dialog.setOnCancelListener(x -> promise.reject("CANCELLED", "已取消")); dialog.setOnDismissListener(x -> bitmap.recycle()); dialog.show();
        } catch (Exception e) { promise.reject("AVATAR", e); }
    }
}
