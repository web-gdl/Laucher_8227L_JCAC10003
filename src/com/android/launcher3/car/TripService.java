package com.android.launcher3.car;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;

import com.android.launcher3.Diag;
import com.android.launcher3.HomeActivity;
import com.android.launcher3.LauncherApp;
import com.android.launcher3.R;

/**
 * Servicio en primer plano que mantiene el GPS a 1 posición por segundo mientras el auto está encendido,
 * aunque otra app (navegador, música) esté delante. Muestra una notificación silenciosa «Odómetro».
 */
public final class TripService extends Service {

    private static final String CHANNEL = "odometro";
    private static final int NOTIFICATION_ID = 8227;

    public static void start(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try { c.startForegroundService(new Intent(c, TripService.class)); }
        catch (RuntimeException e) { Diag.warn("gps", "startForegroundService", e); }
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, TripService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.odo_channel), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, HomeActivity.class), 0);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_speed)
                .setContentTitle(getString(R.string.odo_channel))
                .setContentText(getString(R.string.odo_notification))
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, n);
        LauncherApp.get().gps().start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        LauncherApp.get().gps().stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
