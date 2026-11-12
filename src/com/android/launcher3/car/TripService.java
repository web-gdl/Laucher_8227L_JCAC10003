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
 *
 * Regla de Android 8: CADA llamada a startForegroundService() obliga a llamar a startForeground() en menos de 5 s,
 * o el sistema declara la app «sin responder» y la cierra. El estéreo avisa del ACC varias veces (lectura inicial,
 * callback y broadcast), así que:
 *  - {@link #start} solo pide el arranque una vez (si ya está pedido o en marcha, no hace nada);
 *  - onStartCommand llama siempre a startForeground();
 *  - {@link #stop} nunca usa stopService() mientras el arranque está pendiente: marca que ya no se quiere y el
 *    propio servicio se detiene después de haber cumplido con startForeground().
 */
public final class TripService extends Service {

    private static final String CHANNEL = "odometro";
    private static final int NOTIFICATION_ID = 8227;

    // Solo hilo principal
    private static boolean wanted;      // el auto está encendido y hay permiso
    private static boolean requested;   // startForegroundService() pedido y aún no atendido por onStartCommand
    private static TripService instance;

    public static void start(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        wanted = true;
        if (instance != null || requested) return;
        try {
            requested = true;
            Guard.begin(Guard.BG);
            c.getApplicationContext().startForegroundService(new Intent(c.getApplicationContext(), TripService.class));
        } catch (RuntimeException e) {
            requested = false;
            Diag.warn("gps", "startForegroundService", e);
        }
    }

    public static void stop(Context c) {
        wanted = false;
        if (instance != null && !requested) instance.stopSelf();
        // si el arranque está pendiente, onStartCommand lo detendrá tras llamar a startForeground()
    }

    public static boolean isActive() { return instance != null; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        goForeground();
        try { LauncherApp.get().gps().start(); } catch (RuntimeException e) { Diag.warn("gps", "inicio del GPS", e); }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground(); // siempre: lo exige cada startForegroundService()
        requested = false;
        if (!wanted) stopSelf();
        return START_NOT_STICKY;
    }

    private void goForeground() {
        try {
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
        } catch (RuntimeException e) {
            Diag.warn("gps", "startForeground", e);
        }
    }

    @Override public void onDestroy() {
        instance = null;
        try { LauncherApp.get().gps().stop(); } catch (RuntimeException e) { Diag.warn("gps", "parada del GPS", e); }
        super.onDestroy();
        // Decide qué toca ahora: GPS directo con el inicio visible, o volver a arrancar si llegó otro encendido
        try { LauncherApp.get().gps().sync(); } catch (RuntimeException e) { Diag.warn("gps", "sync", e); }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
