package com.android.launcher3;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Registro circular en memoria (últimas líneas) para la pantalla de diagnóstico.
 * Barato: una línea por evento relevante, nunca en bucles de dibujo.
 */
public final class Diag {
    private static final String TAG = "LauncherNativo";
    private static final int MAX_LINES = 250;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>(MAX_LINES);
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static final Date SCRATCH = new Date();

    private Diag() {}

    public static void log(String where, String msg) {
        Log.d(TAG, where + ": " + msg);
        synchronized (LINES) {
            if (LINES.size() >= MAX_LINES) LINES.removeFirst();
            SCRATCH.setTime(System.currentTimeMillis());
            LINES.addLast(TIME.format(SCRATCH) + ' ' + where + ": " + msg);
        }
    }

    public static void warn(String where, String msg, Throwable t) {
        Log.w(TAG, where + ": " + msg, t);
        log(where, msg + (t != null ? " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")" : ""));
    }

    public static String dump() {
        StringBuilder sb = new StringBuilder(8192);
        synchronized (LINES) {
            for (String l : LINES) sb.append(l).append('\n');
        }
        return sb.toString();
    }
}
