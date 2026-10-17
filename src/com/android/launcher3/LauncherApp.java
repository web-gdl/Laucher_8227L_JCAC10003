package com.android.launcher3;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.android.launcher3.jancar.JancarBridge;
import com.android.launcher3.media.SessionProbe;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Proceso del launcher (persistente, uid system). Mantiene los tres objetos de larga vida:
 * preferencias, repositorio de apps (con su hilo de fondo) y el puente a com.jancar.services.
 */
public final class LauncherApp extends Application {
    /** Igual que el launcher de fábrica: 6 s tras arrancar pide al servicio reabrir la última app multimedia. */
    private static final long MEMORY_APP_DELAY_MS = 6000;

    private static LauncherApp instance;
    private Prefs prefs;
    private AppsRepository apps;
    private JancarBridge bridge;
    private SessionProbe sessions;
    private final long startedAt = SystemClock.elapsedRealtime();
    private boolean memoryAppRequested;

    public static LauncherApp get() { return instance; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        installCrashLogger();
        Diag.log("app", "inicio " + BuildInfo.version(this));
        prefs = new Prefs(this);
        apps = new AppsRepository(this);
        bridge = new JancarBridge(this);
        bridge.start();
        sessions = new SessionProbe(this);
        sessions.start();
        apps.refresh();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { maybeOpenMemoryApp(); }
        }, MEMORY_APP_DELAY_MS);
    }

    private void maybeOpenMemoryApp() {
        if (memoryAppRequested) return;
        memoryAppRequested = true;
        // El launcher de fábrica solo lo pide si él mismo está en primer plano (es decir, arranque normal).
        if (!BuildInfo.isReplacement(this)) Diag.log("system", "openMemoryApp omitido: variante de prueba");
        else if (bridge.isHomeVisible()) bridge.openMemoryApp();
        else Diag.log("system", "openMemoryApp omitido: launcher no visible");
    }

    public Prefs prefs() { return prefs; }

    public AppsRepository apps() { return apps; }

    public JancarBridge bridge() { return bridge; }

    public SessionProbe sessions() { return sessions; }

    public long uptimeMs() { return SystemClock.elapsedRealtime() - startedAt; }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            apps.trimIcons(true);
            Diag.log("app", "trim " + level + ": caché de iconos vaciada");
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            apps.trimIcons(false);
        }
    }

    /**
     * El de fábrica (CrashLog) mataba el proceso ante cualquier excepción; aquí solo dejamos
     * constancia en un archivo propio y dejamos actuar al manejador del sistema.
     */
    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    File f = new File(getFilesDir(), "crash.txt");
                    PrintWriter pw = new PrintWriter(new FileWriter(f, false));
                    pw.println("version " + BuildInfo.version(LauncherApp.this) + " uptime " + uptimeMs() + " ms, hilo " + t.getName());
                    e.printStackTrace(pw);
                    pw.println();
                    pw.print(Diag.dump());
                    pw.close();
                } catch (Throwable ignored) {}
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }
}
