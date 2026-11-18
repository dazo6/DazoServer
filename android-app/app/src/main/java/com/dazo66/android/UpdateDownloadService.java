package com.dazo66.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps an update transferring while the user switches apps. No server credentials are used. */
public final class UpdateDownloadService extends Service {
    private static final String CHANNEL = "app-updates", CANCEL = "cancel-update";
    private static final int NOTIFICATION = 73;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private UpdateManager updates;

    @Override public void onCreate() {
        super.onCreate(); updates = UpdateManager.get(this);
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "应用更新", NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification(long downloaded, long total) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
        PendingIntent cancel = PendingIntent.getService(this, 1, new Intent(this, UpdateDownloadService.class).setAction(CANCEL), flags);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Dazo 应用更新").setContentText(total > 0 ? "正在下载 " + downloaded * 100 / total + "%" : "正在连接更新服务…")
                .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
                .setProgress(100, total > 0 ? (int) (downloaded * 100 / total) : 0, total <= 0)
                .addAction(new Notification.Action.Builder(null, "取消", cancel).build()).build();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && CANCEL.equals(intent.getAction())) { updates.cancel(); stopSelf(); return START_NOT_STICKY; }
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(0, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIFICATION, notification(0, 0));
        if (running.compareAndSet(false, true)) worker.execute(() -> {
            try { updates.transfer((done, total) -> getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(done, total))); }
            finally { running.set(false); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
        });
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId, int fgsType) { updates.interrupt(); stopSelf(); }
    @Override public void onDestroy() {
        if (running.get()) updates.interrupt();
        worker.shutdownNow(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
