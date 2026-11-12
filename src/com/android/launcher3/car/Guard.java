package com.android.launcher3.car;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.android.launcher3.Diag;

/**
 * Seguro de arranque de los instrumentos. El inicio tiene que abrir siempre, pase lo que pase con el GPS.
 *
 * Los instrumentos se activan por etapas, y cada etapa se marca como «pendiente» en disco antes de empezar y como
 * «correcta» si el proceso sigue vivo unos segundos después:
 *   ui  → dibujar el velocímetro y la brújula;
 *   gps → pedir posiciones al GPS (solo con el inicio visible);
 *   bg  → servicio en primer plano que sigue midiendo con otra app delante.
 * Si el launcher se congela o se cierra con una etapa pendiente, en el siguiente arranque esa etapa queda
 * desactivada sola (y Diagnóstico dice cuál fue). Una etapa ya comprobada no se vuelve a vigilar.
 * El GPS, además, no se usa hasta que el usuario toca la tarjeta para activarlo.
 */
public final class Guard {
    public static final String UI = "ui", GPS = "gps", BG = "bg";
    private static final String[] PHASES = {UI, GPS, BG};
    public static final long UI_WINDOW_MS = 6000, WINDOW_MS = 15000;

    private static SharedPreferences p;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private Guard() {}

    /** Al nacer el proceso: lo que quedó pendiente la vez anterior falló. */
    public static void init(Context c) {
        p = c.getApplicationContext().getSharedPreferences("guard", Context.MODE_PRIVATE);
        SharedPreferences.Editor e = null;
        for (String ph : PHASES) {
            if (!p.getBoolean("pending_" + ph, false)) continue;
            if (e == null) e = p.edit();
            e.putBoolean("pending_" + ph, false).putBoolean("tripped_" + ph, true);
            if (ph.equals(GPS)) e.putBoolean("enabled_gps", false);
            if (ph.equals(BG)) e.putBoolean("enabled_bg", false);
            Diag.log("seguro", "la etapa «" + ph + "» no terminó en el arranque anterior: desactivada");
        }
        if (e != null) e.commit();
    }

    /** Antes de una etapa aún no comprobada. Escribe en disco de inmediato (commit). */
    public static void begin(final String phase) {
        if (p == null || p.getBoolean("ok_" + phase, false) || p.getBoolean("pending_" + phase, false)) return;
        p.edit().putBoolean("pending_" + phase, true).commit();
        main.postDelayed(() -> {
            p.edit().putBoolean("pending_" + phase, false).putBoolean("ok_" + phase, true).putBoolean("tripped_" + phase, false).apply();
            Diag.log("seguro", "etapa «" + phase + "» correcta");
        }, phase.equals(UI) ? UI_WINDOW_MS : WINDOW_MS);
    }

    public static boolean pending(String phase) { return p != null && p.getBoolean("pending_" + phase, false); }

    public static boolean ok(String phase) { return p != null && p.getBoolean("ok_" + phase, false); }

    public static boolean tripped(String phase) { return p != null && p.getBoolean("tripped_" + phase, false); }

    /** El usuario quiere reintentar una etapa que falló. */
    public static void retry(String phase) {
        if (p != null) p.edit().putBoolean("tripped_" + phase, false).putBoolean("ok_" + phase, false).apply();
    }

    /** GPS activado por el usuario (toque en la tarjeta). Por defecto, apagado. */
    public static boolean gpsEnabled() { return p != null && p.getBoolean("enabled_gps", false); }

    public static void setGpsEnabled(boolean on) {
        if (p == null) return;
        p.edit().putBoolean("enabled_gps", on).putBoolean("tripped_gps", false).apply();
    }

    /** Seguir midiendo con otra app delante (servicio en primer plano). Por defecto, sí. */
    public static boolean bgEnabled() { return p != null && p.getBoolean("enabled_bg", true); }

    public static void setBgEnabled(boolean on) {
        if (p == null) return;
        p.edit().putBoolean("enabled_bg", on).putBoolean("tripped_bg", false).apply();
    }

    public static String describe() {
        if (p == null) return "sin iniciar";
        StringBuilder sb = new StringBuilder(96);
        sb.append("GPS ").append(gpsEnabled() ? "activado" : "sin activar").append(" · segundo plano ").append(bgEnabled() ? "sí" : "no");
        for (String ph : PHASES) {
            sb.append(" · ").append(ph).append('=')
                    .append(tripped(ph) ? "FALLÓ" : pending(ph) ? "probando" : ok(ph) ? "ok" : "sin probar");
        }
        return sb.toString();
    }
}
