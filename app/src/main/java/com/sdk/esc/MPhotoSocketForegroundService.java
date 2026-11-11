package com.sdk.esc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/** Keeps Mono realtime socket independent from Activity lifecycle. */
public final class MPhotoSocketForegroundService extends Service {
    private static final String CHANNEL_ID = "mphoto_mono_socket";
    private static final int NOTIFICATION_ID = 4201;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "M-Photo Mono connection", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("M-Photo Mono")
                .setContentText("Đang duy trì kết nối máy với server")
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
        startForeground(NOTIFICATION_ID, notification);
        SocketService socket = SocketService.getInstance();
        socket.attachAppContext(this);
        socket.connect();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
