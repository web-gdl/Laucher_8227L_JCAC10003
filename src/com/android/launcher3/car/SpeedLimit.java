package com.android.launcher3.car;

/**
 * Cuándo avisar por exceso de velocidad. Java puro (se prueba fuera de Android).
 *  - Avisa si se supera el límite durante {@link #CONFIRM_MS} seguidos (un pico del GPS no cuenta).
 *  - Mientras se siga por encima, repite el aviso cada {@link #REPEAT_MS}.
 *  - Vuelve a quedar listo para avisar al instante al bajar {@link #HYSTERESIS_KMH} por debajo del límite.
 */
public final class SpeedLimit {
    static final long CONFIRM_MS = 2000;
    static final long REPEAT_MS = 30000;
    static final float HYSTERESIS_KMH = 3f;

    private int limitKmh;
    private long overSince = -1;
    private long lastWarn = -1;

    public SpeedLimit(int limitKmh) { setLimit(limitKmh); }

    /** 0 = desactivado. */
    public void setLimit(int kmh) {
        limitKmh = Math.max(0, kmh);
        overSince = -1;
        lastWarn = -1;
    }

    public int limit() { return limitKmh; }

    public boolean enabled() { return limitKmh > 0; }

    /** @return true si hay que avisar ahora. {@code kmh} negativo = velocidad desconocida. */
    public boolean onSpeed(float kmh, long nowMs) {
        if (limitKmh <= 0 || kmh < 0) { overSince = -1; return false; }
        if (kmh > limitKmh) {
            if (overSince < 0) overSince = nowMs;
            if (nowMs - overSince >= CONFIRM_MS && (lastWarn < 0 || nowMs - lastWarn >= REPEAT_MS)) {
                lastWarn = nowMs;
                return true;
            }
        } else {
            overSince = -1;
            if (kmh <= limitKmh - HYSTERESIS_KMH) lastWarn = -1;
        }
        return false;
    }

    public boolean isOver(float kmh) { return limitKmh > 0 && kmh > limitKmh; }
}
