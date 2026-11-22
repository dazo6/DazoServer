package com.dazo66.android;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FileUploadService extends Service {
    private static final String CHANNEL = "file-uploads";
    private static final int ID = 75;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private FileUploads uploads;
    private int latestStartId;
    private volatile boolean stopping;
    private long notifiedAt;
    private String notifiedName = "";
    @Override public void onCreate() {
        super.onCreate(); uploads = FileUploads.get(this);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "文件上传", NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification(String name, long bytes, long total) {
        Intent target = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(this, ID, target, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int percent = total > 0 ? (int)Math.min(99, bytes * 100 / total) : 0;
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(name).setContentText("正在上传 " + percent + "%")
            .setProgress(100, percent, total <= 0).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).build();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification(getString(R.string.app_name) + " 文件上传", 0, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(ID, notification(getString(R.string.app_name) + " 文件上传", 0, 0));
        if (running.compareAndSet(false, true)) worker.execute(() -> {
            try { uploads.runQueue((name, bytes, total) -> {
                long now = System.currentTimeMillis();
                if (!name.equals(notifiedName) || now - notifiedAt >= 500) {
                    notifiedAt = now; notifiedName = name;
                    try { getSystemService(NotificationManager.class).notify(ID, notification(name, bytes, total)); } catch (RuntimeException ignored) {}
                }
            }); }
            finally { new Handler(getMainLooper()).post(() -> {
                running.set(false);
                if (!stopping && uploads.hasWork()) onStartCommand(null, 0, latestStartId);
                else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelfResult(latestStartId); }
            }); }
        });
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId, int fgsType) { stopping = true; uploads.interrupt(); worker.shutdownNow(); stopSelf(); }
    @Override public void onDestroy() { stopping = true; if (running.get()) uploads.interrupt(); worker.shutdownNow(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
