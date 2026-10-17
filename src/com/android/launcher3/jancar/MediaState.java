package com.android.launcher3.jancar;

import android.graphics.Bitmap;

/** Instantánea de lo que el servicio del estéreo dice que suena. Solo se modifica en el hilo principal. */
public final class MediaState {
    // Tipos de fuente del servicio Jancar (IVIMedia.Type)
    public static final int TYPE_NONE = -1;
    public static final int TYPE_MUSIC = 0;
    public static final int TYPE_RADIO = 1;
    public static final int TYPE_VIDEO = 2;
    public static final int TYPE_A2DP = 7;
    public static final int TYPE_THIRD_PARTY = 20;

    // Estados (IVIMedia.MediaState)
    public static final int STATE_STOPPED = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_PLAYING = 3;

    public int type = TYPE_NONE;
    public int state = STATE_STOPPED;
    public String title = "";
    public String artist = "";
    public Bitmap cover;
    public int index;
    public int total;
    /** Posición y duración en segundos (así las publica el servicio). */
    public int position;
    public int duration;
    public long lastInfoAt;
    public long lastStateAt;
    /**
     * Paquete de la app que suena cuando se conoce (fuentes de terceros, vía MediaSession).
     * El servicio Jancar no lo dice; null si no se sabe.
     */
    public String sourcePackage;
    /** true si estado y posición vienen de la sesión multimedia de la app, no del servicio Jancar. */
    public boolean fromSession;

    public boolean isPlaying() { return state == STATE_PLAYING; }

    public boolean hasContent() { return type != TYPE_NONE && (title != null && !title.isEmpty()); }

    public boolean isThirdParty() { return type == TYPE_THIRD_PARTY; }

    @Override public String toString() {
        return "type=" + type + " state=" + state + " title='" + title + "' artist='" + artist + "' pos=" + position + "/" + duration
                + " cover=" + (cover != null ? cover.getWidth() + "x" + cover.getHeight() : "no")
                + (sourcePackage != null ? " pkg=" + sourcePackage : "") + (fromSession ? " (sesión)" : "");
    }
}
