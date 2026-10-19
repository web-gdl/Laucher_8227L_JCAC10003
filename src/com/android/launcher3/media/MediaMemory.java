package com.android.launcher3.media;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;

import java.util.List;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.android.launcher3.BuildInfo;
import com.android.launcher3.Diag;
import com.android.launcher3.jancar.JancarBridge;
import com.android.launcher3.jancar.MediaState;

/**
 * Recuerda la última canción y la app que la tocaba, y vuelve a abrir esa app («app de memoria»)
 * cuando el estéreo arranca o despierta, como hace el launcher de fábrica.
 *
 * Por qué hace falta: el launcher de fábrica pide al servicio Jancar {@code openMemoryApp} 6 s después
 * de arrancar, y el servicio abre la última app de música con el extra {@code boot_memory=true}
 * (RadilloPlayerWave y la música de fábrica restauran entonces su cola y siguen sonando). Si nuestro
 * launcher es la pantalla de inicio, eso ya no ocurre; y el servicio parece comprobar que la actividad
 * superior sea {@code com.android.launcher3}, así que la variante de prueba abre la app ella misma.
 *
 * Vive en el proceso (no en la Activity) para seguir el estado aunque otra app esté en primer plano.
 */
public final class MediaMemory implements JancarBridge.Listener {

    public static final String EXTRA_BOOT_MEMORY = "boot_memory";
    private static final String FILE = "media_memory";
    /** Solo se considera «arranque del estéreo» si el sistema lleva encendido menos de esto. */
    private static final long FRESH_BOOT_MS = 4 * 60 * 1000L;
    private static final long BOOT_DELAY_MS = 6000;       // igual que el launcher de fábrica
    private static final long BOOT_GIVE_UP_MS = 45000;     // esperar al servicio como mucho esto
    private static final long WAKE_CHECK_MS = 3000;        // tras ACC ON, margen para que la app reanude sola
    private static final long WAKE_RETRY_MS = 5000;        // tras avisar a la app, margen antes del respaldo
    /** Tras encender o arrancar, el servicio pasa un rato sin fuente: no cuenta como «el usuario la cerró». */
    private static final long SETTLE_MS = 30000;
    public static final String ACTION_ACC = "com.jancar.services.action.acc";

    private final Context app;
    private final JancarBridge bridge;
    private final SessionProbe sessions;
    private final SharedPreferences sp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final long createdAt = SystemClock.elapsedRealtime();

    private String title, artist, pkg;
    private boolean accOff;
    /** Había música al apagar (persistido: el servicio cierra este proceso al apagar el auto). */
    private boolean active;
    private long settleUntil;
    private boolean bootDone;
    private String lastAction = "-";

    public MediaMemory(Context context, JancarBridge bridge, SessionProbe sessions) {
        app = context.getApplicationContext();
        this.bridge = bridge;
        this.sessions = sessions;
        sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        title = sp.getString("title", null);
        artist = sp.getString("artist", null);
        pkg = sp.getString("pkg", null);
        active = sp.getBoolean("active", false);
        settleUntil = SystemClock.elapsedRealtime() + SETTLE_MS;
        bridge.addListener(this);
    }

    // ------------------------------------------------------------------ lectura

    public boolean hasLast() { return title != null && !title.isEmpty(); }

    public String getTitle() { return title; }

    public String getArtist() { return artist; }

    public String getPackage() { return pkg; }

    public String describe() {
        return "última: " + (hasLast() ? "'" + title + "' — " + artist : "ninguna") + " · app " + pkg
                + " · sonaba al apagar: " + (active ? "sí" : "no")
                + " · ACC " + (accOff ? "apagado" : "encendido") + " · última acción: " + lastAction;
    }

    // ------------------------------------------------------------------ JancarBridge.Listener

    @Override public void onMediaChanged(MediaState s) {
        if (!s.hasContent()) {
            // Sin fuente con el auto encendido y pasado el arranque: el usuario cerró la música.
            if (active && !accOff && SystemClock.elapsedRealtime() > settleUntil) setActive(false);
            return;
        }
        if (!accOff && !active) setActive(true);
        boolean changed = false, newTrack = !eq(title, s.title) || !eq(artist, s.artist);
        if (newTrack) {
            title = s.title;
            artist = s.artist;
            changed = true;
        }
        // El paquete solo se resuelve al cambiar de canción (el servicio repite la misma info cada pocos segundos).
        if (newTrack || pkg == null || s.sourcePackage != null) {
            String p = resolvePackage(s);
            if (p != null && !p.equals(pkg)) { pkg = p; changed = true; }
        }
        if (changed) {
            sp.edit().putString("title", title).putString("artist", artist).putString("pkg", pkg).apply();
        }
    }

    @Override public void onServicesChanged() {}

    /**
     * Llega del callback del servicio, del broadcast y también con la lectura inicial al conectar
     * (el proceso suele nacer con el auto aún apagado, porque el servicio lo cierra al apagar).
     */
    @Override public void onAccChanged(boolean on) {
        if (!on) {
            if (!accOff) {
                accOff = true;
                main.removeCallbacks(wakeCheck);
                main.removeCallbacks(wakeFallback);
                Diag.log("memoria", "ACC OFF (sonaba: " + active + ")");
            }
        } else if (accOff) {
            accOff = false;
            settleUntil = SystemClock.elapsedRealtime() + SETTLE_MS;
            Diag.log("memoria", "ACC ON (sonaba al apagar: " + active + ")");
            main.postDelayed(wakeCheck, WAKE_CHECK_MS);
        }
    }

    private void setActive(boolean v) {
        active = v;
        sp.edit().putBoolean("active", v).apply();
    }

    @Override public void onCcdChanged(int status) {}

    /**
     * Al encender: si la música no volvió sola, se avisa a la app de memoria con el aviso de la llave
     * dirigido a ella (llega aunque la app esté cerrada). Las apps que lo escuchan, como
     * RadilloPlayerWave, deciden si se reabren (solo si sonaban al apagar). Si la app no lo escucha,
     * se la abre con boot_memory, solo si sonaba y desde la pantalla de inicio.
     */
    private final Runnable wakeCheck = new Runnable() {
        @Override public void run() {
            if (bridge.getMedia().hasContent()) { lastAction = "encender: la música siguió sola"; Diag.log("memoria", lastAction); return; }
            String target = targetPackage();
            if (target == null) { lastAction = "encender: sin app de memoria"; Diag.log("memoria", lastAction); return; }
            if (sendAccOn(target)) {
                lastAction = "encender: aviso de llave enviado a " + target;
                Diag.log("memoria", lastAction);
                return;
            }
            main.postDelayed(wakeFallback, 0);
        }
    };

    private final Runnable wakeFallback = new Runnable() {
        @Override public void run() {
            if (bridge.getMedia().hasContent()) return;
            if (!active) { lastAction = "encender: no sonaba al apagar"; Diag.log("memoria", lastAction); return; }
            // No tapar la navegación u otra app que el usuario tenga delante.
            if (!bridge.isHomeVisible()) { lastAction = "encender: omitido (inicio no visible)"; Diag.log("memoria", lastAction); return; }
            openApp("encender");
        }
    };

    /** Envía com.jancar.services.action.acc (acc=true) solo a {@code target}; false si no tiene receptor. */
    private boolean sendAccOn(String target) {
        Intent i = new Intent(ACTION_ACC).setPackage(target).putExtra("acc", true)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
        List<ResolveInfo> r;
        try { r = app.getPackageManager().queryBroadcastReceivers(i, 0); } catch (RuntimeException e) { r = null; }
        if (r == null || r.isEmpty()) return false;
        try {
            app.sendBroadcast(i);
            return true;
        } catch (RuntimeException e) {
            Diag.warn("memoria", "aviso de llave a " + target, e);
            return false;
        }
    }

    private String targetPackage() {
        String t = bridge.getMemoryAppPackage();
        if (t == null || t.isEmpty()) t = pkg;
        return t == null || t.isEmpty() ? null : t;
    }

    // ------------------------------------------------------------------ arranque del estéreo

    /** Lo llama LauncherApp al crearse el proceso. Solo actúa en un arranque real del estéreo. */
    public void scheduleBoot() {
        if (SystemClock.elapsedRealtime() > FRESH_BOOT_MS) {
            lastAction = "arranque: omitido (el estéreo ya estaba encendido)";
            Diag.log("memoria", lastAction);
            return;
        }
        main.postDelayed(bootStep, BOOT_DELAY_MS);
    }

    private final Runnable bootStep = new Runnable() {
        @Override public void run() {
            if (bootDone) return;
            long waited = SystemClock.elapsedRealtime() - createdAt;
            boolean ready = bridge.isSystemOnline() && bridge.isHomeVisible();
            if (!ready && waited < BOOT_GIVE_UP_MS) { main.postDelayed(this, 2000); return; }
            bootDone = true;
            if (!bridge.isHomeVisible()) { lastAction = "arranque: omitido (inicio no visible)"; Diag.log("memoria", lastAction); return; }
            if (bridge.getMedia().hasContent()) { lastAction = "arranque: ya suena algo"; Diag.log("memoria", lastAction); return; }
            if (BuildInfo.isReplacement(app) && bridge.openMemoryApp()) {
                // Misma identidad que el de fábrica: el servicio abre la app con boot_memory=true.
                lastAction = "arranque: openMemoryApp del servicio";
                Diag.log("memoria", lastAction);
                return;
            }
            String target = targetPackage();
            if (target != null && sendAccOn(target)) {
                lastAction = "arranque: aviso enviado a " + target;
                Diag.log("memoria", lastAction);
                return;
            }
            openApp("arranque");
        }
    };

    // ------------------------------------------------------------------ abrir la app de memoria

    /** Abre la app de memoria con boot_memory=true (la app decide si sigue sonando). */
    public boolean openApp(String why) {
        String target = targetPackage();
        if (target == null) { lastAction = why + ": sin app conocida"; Diag.log("memoria", lastAction); return false; }
        Intent i = app.getPackageManager().getLaunchIntentForPackage(target);
        if (i == null) { lastAction = why + ": " + target + " no instalada"; Diag.log("memoria", lastAction); return false; }
        i.putExtra(EXTRA_BOOT_MEMORY, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            app.startActivity(i);
            lastAction = why + ": abierta " + target;
            Diag.log("memoria", lastAction);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            lastAction = why + ": error al abrir " + target;
            Diag.warn("memoria", lastAction, e);
            return false;
        }
    }

    // ------------------------------------------------------------------ utilidades

    private String resolvePackage(MediaState s) {
        if (s.sourcePackage != null) return s.sourcePackage;
        if (s.isThirdParty() && sessions.hasSession()) return sessions.getPackageName();
        switch (s.type) {
            case MediaState.TYPE_A2DP:
            case MediaState.TYPE_RADIO:
            case MediaState.TYPE_VIDEO:
                return null; // fuentes del sistema: no se reabren desde aquí
            default:
                String m = bridge.getMemoryAppPackage(); // la app registrada como fuente
                return m != null && !m.isEmpty() ? m : null;
        }
    }

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }
}
