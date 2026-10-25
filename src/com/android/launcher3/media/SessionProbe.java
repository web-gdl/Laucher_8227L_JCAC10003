package com.android.launcher3.media;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.android.launcher3.Diag;
import com.android.launcher3.jancar.MediaState;

import java.util.ArrayList;
import java.util.List;

/**
 * Observa las sesiones multimedia de Android (MediaSessionManager) para saber qué app de terceros
 * está sonando y en qué estado. Hace falta porque el servicio Jancar publica las fuentes de
 * terceros (tipo 20) sin decir el paquete y, según lo observado en el equipo, con estado 1 y
 * posición 0/0.
 *
 * Necesita el permiso de firma MEDIA_CONTENT_CONTROL (lo concede la firma de plataforma). Si no
 * se tiene, la sonda queda desactivada sin error y el launcher funciona solo con el servicio.
 *
 * Sin sondeo: solo callbacks del sistema; todo el estado se toca en el hilo principal.
 */
public final class SessionProbe {

    public interface Listener { void onSessionChanged(SessionProbe probe); }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final String selfPackage;
    private MediaSessionManager msm;
    private boolean started;
    private boolean available;
    private String note = "no iniciada";
    private MediaController current;
    private int sessionsSeen;
    private int changes;

    // Sesión elegida
    private String packageName;
    private int playbackState = PlaybackState.STATE_NONE;
    private long positionMs;
    private long positionAt;   // elapsedRealtime en que positionMs era válida
    private float speed;
    private long durationMs;
    private String title = "";
    private String artist = "";
    private long updatedAt;

    public SessionProbe(Context context) {
        app = context.getApplicationContext();
        selfPackage = app.getPackageName();
    }

    // ------------------------------------------------------------------ ciclo de vida

    public void start() {
        if (started) return;
        started = true;
        msm = (MediaSessionManager) app.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (msm == null) { note = "sin MediaSessionManager"; return; }
        try {
            List<MediaController> list = msm.getActiveSessions(null);
            msm.addOnActiveSessionsChangedListener(sessionsListener, null, main);
            available = true;
            note = "activa";
            Diag.log("sesiones", "sonda activa, " + list.size() + " sesiones");
            pick(list);
        } catch (SecurityException e) {
            available = false;
            note = "sin permiso MEDIA_CONTENT_CONTROL";
            Diag.log("sesiones", note + " (solo con firma de plataforma)");
        } catch (RuntimeException e) {
            available = false;
            note = "error: " + e.getClass().getSimpleName();
            Diag.warn("sesiones", "getActiveSessions", e);
        }
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override public void onActiveSessionsChanged(List<MediaController> controllers) {
                    pick(controllers);
                }
            };

    /** Vuelve a leer la lista del sistema (p. ej. cuando una sesión se destruye). */
    private void repick() {
        if (!available || msm == null) return;
        try { pick(msm.getActiveSessions(null)); }
        catch (RuntimeException e) { Diag.warn("sesiones", "repick", e); }
    }

    /**
     * Elige la sesión que representa "lo que suena": la que esté reproduciendo; si ninguna, la
     * primera con estado (el sistema las ordena por actividad reciente). Se ignoran la propia,
     * la del servicio Jancar y la de Bluetooth, igual que hace el servicio.
     */
    private void pick(List<MediaController> list) {
        sessionsSeen = list != null ? list.size() : 0;
        MediaController best = null;
        int bestScore = -1;
        if (list != null) {
            for (MediaController c : list) {
                if (c == null) continue;
                String pkg = c.getPackageName();
                if (pkg == null || pkg.equals(selfPackage) || isSystemSource(pkg)) continue;
                PlaybackState ps = null;
                try { ps = c.getPlaybackState(); } catch (RuntimeException ignored) {}
                int score;
                if (ps == null) score = 0;
                else if (isPlayingState(ps.getState())) score = 3;
                else if (ps.getState() == PlaybackState.STATE_PAUSED) score = 2;
                else score = 1;
                if (score > bestScore) { best = c; bestScore = score; }
            }
        }
        setCurrent(best);
    }

    private static boolean isSystemSource(String pkg) {
        return pkg.equals("com.jancar.services") || pkg.equals("com.android.bluetooth") || pkg.startsWith("com.jancar.bt");
    }

    private void setCurrent(MediaController c) {
        if (current != null && c != null && sameSession(current, c)) {
            readAll(c);
            notifyChanged();
            return;
        }
        if (current != null) {
            try { current.unregisterCallback(callback); } catch (RuntimeException ignored) {}
        }
        current = c;
        if (c != null) {
            try { c.registerCallback(callback, main); } catch (RuntimeException e) { Diag.warn("sesiones", "registerCallback", e); }
            readAll(c);
            Diag.log("sesiones", "fuente " + packageName + " estado " + playbackState);
        } else if (packageName != null) {
            Diag.log("sesiones", "sin sesión de terceros");
            clear();
        } else {
            clear();
        }
        notifyChanged();
    }

    private static boolean sameSession(MediaController a, MediaController b) {
        try { return a.getSessionToken().equals(b.getSessionToken()); } catch (RuntimeException e) { return false; }
    }

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onSessionDestroyed() {
            Diag.log("sesiones", "sesión destruida: " + packageName);
            repick();
        }

        @Override public void onPlaybackStateChanged(PlaybackState state) {
            int before = playbackState;
            readState(state);
            if (before != playbackState) Diag.log("sesiones", packageName + " estado " + playbackState);
            notifyChanged();
        }

        @Override public void onMetadataChanged(MediaMetadata metadata) {
            String before = title;
            readMeta(metadata);
            if (!before.equals(title)) Diag.log("sesiones", packageName + " '" + title + "' — " + artist);
            notifyChanged();
        }
    };

    private void readAll(MediaController c) {
        packageName = c.getPackageName();
        PlaybackState ps = null;
        MediaMetadata md = null;
        try { ps = c.getPlaybackState(); md = c.getMetadata(); } catch (RuntimeException ignored) {}
        readState(ps);
        readMeta(md);
    }

    private void readState(PlaybackState ps) {
        updatedAt = SystemClock.elapsedRealtime();
        if (ps == null) {
            playbackState = PlaybackState.STATE_NONE;
            positionMs = 0; positionAt = updatedAt; speed = 0f;
            return;
        }
        playbackState = ps.getState();
        positionMs = Math.max(0, ps.getPosition());
        long t = ps.getLastPositionUpdateTime();
        positionAt = t > 0 ? t : updatedAt;
        speed = ps.getPlaybackSpeed();
    }

    private void readMeta(MediaMetadata md) {
        if (md == null) { durationMs = 0; title = ""; artist = ""; return; }
        durationMs = Math.max(0, md.getLong(MediaMetadata.METADATA_KEY_DURATION));
        title = firstNonEmpty(md.getString(MediaMetadata.METADATA_KEY_TITLE), md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
        artist = firstNonEmpty(md.getString(MediaMetadata.METADATA_KEY_ARTIST), md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    private void clear() {
        packageName = null;
        playbackState = PlaybackState.STATE_NONE;
        positionMs = 0; positionAt = 0; speed = 0f; durationMs = 0;
        title = ""; artist = "";
    }

    private void notifyChanged() {
        changes++;
        for (Listener l : new ArrayList<>(listeners)) l.onSessionChanged(this);
    }

    // ------------------------------------------------------------------ lectura

    public boolean isAvailable() { return available; }

    public boolean hasSession() { return current != null && packageName != null; }

    public String getPackageName() { return packageName; }

    public String getTitle() { return title; }

    public String getArtist() { return artist; }

    private static boolean isPlayingState(int s) {
        return s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
                || s == PlaybackState.STATE_FAST_FORWARDING || s == PlaybackState.STATE_REWINDING;
    }

    /** Estado traducido a los códigos del servicio Jancar (1 detenido, 2 pausa, 3 sonando). */
    public int jancarState() {
        if (isPlayingState(playbackState)) return MediaState.STATE_PLAYING;
        if (playbackState == PlaybackState.STATE_PAUSED) return MediaState.STATE_PAUSED;
        return MediaState.STATE_STOPPED;
    }

    /** Posición actual en segundos, extrapolada desde la última actualización si está sonando. */
    public int positionSec() {
        long pos = positionMs;
        if (playbackState == PlaybackState.STATE_PLAYING && speed > 0f && positionAt > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - positionAt) * speed);
        }
        if (durationMs > 0 && pos > durationMs) pos = durationMs;
        return (int) (pos / 1000);
    }

    public int durationSec() { return (int) (durationMs / 1000); }

    /**
     * Fuentes de terceros (tipo 20): el servicio Jancar las publica sin paquete y con estado/posición
     * poco fiables (estado 1, 0/0), así que se completan con la sesión multimedia de la app que suena.
     * Modifica y devuelve {@code m}; solo en el hilo principal.
     */
    public MediaState enrich(MediaState m) {
        if (m.isThirdParty() && hasSession()) {
            m.sourcePackage = packageName;
            m.fromSession = true;
            m.state = jancarState();
            m.position = positionSec();
            m.duration = durationSec();
            if ((m.title == null || m.title.isEmpty()) && !title.isEmpty()) {
                m.title = title;
                if (m.artist == null || m.artist.isEmpty()) m.artist = artist;
            }
        } else {
            m.fromSession = false;
            if (!m.isThirdParty()) m.sourcePackage = null;
        }
        return m;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder(160);
        sb.append(note);
        if (available) sb.append(" · ").append(sessionsSeen).append(" sesiones vistas, ").append(changes).append(" cambios");
        if (hasSession()) {
            sb.append("\n  ").append(packageName).append(" estado=").append(playbackState).append(" (jancar ").append(jancarState()).append(')')
                    .append(" pos=").append(positionSec()).append('/').append(durationSec()).append(" s")
                    .append(" velocidad=").append(speed).append(" '").append(title).append("' — ").append(artist);
        } else if (available) {
            sb.append("\n  sin sesión de terceros");
        }
        return sb.toString();
    }
}
