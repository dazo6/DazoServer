package com.dazo66.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FileDownloadService extends Service {
    private static final String CHANNEL = "file-downloads";
    private static final int ID = 74;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private int latestStartId;
    private FileDownloads downloads;
    @Override public void onCreate() {
        super.onCreate(); downloads = FileDownloads.get(this);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "文件下载", NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification(String name, long done, long total) {
        PendingIntent open = PendingIntent.getActivity(this, 74, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(name)
                .setContentText(total > 0 ? "下载中 " + done * 100 / total + "%" : "正在下载到 " + FileDownloads.LOCATION)
                .setProgress(100, total > 0 ? (int)(done * 100 / total) : 0, total <= 0).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).build();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification(getString(R.string.app_name) + " 文件下载", 0, -1), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(ID, notification(getString(R.string.app_name) + " 文件下载", 0, -1));
        if (running.compareAndSet(false, true)) worker.execute(() -> {
            try { downloads.runQueue((name, bytes, total) -> getSystemService(NotificationManager.class).notify(ID, notification(name, bytes, total))); }
            finally {
                new android.os.Handler(getMainLooper()).post(() -> {
                    running.set(false);
                    if (downloads.hasQueued()) onStartCommand(null, 0, latestStartId);
                    else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelfResult(latestStartId); }
                });
            }
        });
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId, int fgsType) { downloads.interrupt(); stopSelf(); }
    @Override public void onDestroy() { if (running.get()) downloads.interrupt(); worker.shutdownNow(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
