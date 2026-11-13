package com.android.launcher3.car;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.android.launcher3.Diag;
import com.android.launcher3.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Aviso de voz por exceso de velocidad: «Está excediendo el límite de velocidad», con voz femenina en español de México,
 * a volumen alto. Es un archivo de audio grabado (res/raw/exceso_velocidad.ogg), no una voz del sistema: el Android del
 * estéreo no trae motor de voz (TextToSpeech), y así suena igual en cualquier equipo.
 *
 * Mientras suena el aviso:
 *  - la música o el video de cualquier app que respete el enfoque de audio se atenúan (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
 *    Android 8 atenúa solo a las apps que no gestionan el enfoque) y vuelven a su volumen al terminar;
 *  - el volumen multimedia sube al menos al {@link #MIN_VOLUME_PCT} % durante el aviso y se restablece después.
 */
public final class SpeedAlert implements GpsTracker.Listener {

    private static final String KEY = "speed_limit";
    private static final long MAX_WARNING_MS = 12_000;
    /** Volumen multimedia mínimo (porcentaje del máximo) durante el aviso. */
    static final int MIN_VOLUME_PCT = 85;

    private final Context app;
    private final SharedPreferences store;
    private final SpeedLimit limit;
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int warnings;
    private String lastResult = "sin avisos";

    // Estado del aviso en curso (hilo principal)
    private MediaPlayer player;
    private AudioFocusRequest focus;
    private int savedVolume = -1;
    private boolean active;

    public SpeedAlert(Context context) {
        app = context.getApplicationContext();
        store = app.getSharedPreferences("odometer", Context.MODE_PRIVATE);
        audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        limit = new SpeedLimit(store.getInt(KEY, 0));
    }

    public int limitKmh() { return limit.limit(); }

    public boolean isOver(float kmh) { return limit.isOver(kmh); }

    public void setLimitKmh(int kmh) {
        limit.setLimit(kmh);
        store.edit().putInt(KEY, limit.limit()).apply();
        Diag.log("gps", kmh > 0 ? "límite de velocidad " + kmh + " km/h" : "límite de velocidad desactivado");
    }

    @Override public void onGpsChanged(GpsTracker gps) {
        if (limit.onSpeed(gps.speedKmh(), SystemClock.elapsedRealtime())) {
            warnings++;
            Diag.log("gps", "exceso de velocidad: " + Math.round(gps.speedKmh()) + " km/h (límite " + limit.limit() + ")");
            warn();
        }
    }

    /** Reproduce el aviso (también lo usa la prueba: pulsación larga sobre el velocímetro). */
    public void warn() {
        stopPlayer();
        beginDucking();
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(attributes());
            mp.setDataSource(audioFile().getAbsolutePath());
            mp.setVolume(1f, 1f);
            mp.setOnCompletionListener(m -> { lastResult = "reproducido"; endDucking(); });
            mp.setOnErrorListener((m, what, extra) -> {
                lastResult = "error " + what + "/" + extra + " (pitido)";
                Diag.log("gps", "aviso: error del reproductor " + what + "/" + extra);
                beep();
                return true;
            });
            mp.setOnPreparedListener(MediaPlayer::start);
            player = mp;
            mp.prepareAsync();
        } catch (Exception e) {
            Diag.warn("gps", "aviso de voz", e);
            lastResult = "fallo (pitido): " + e.getClass().getSimpleName();
            beep();
        }
    }

    /**
     * El audio está comprimido dentro del APK y openRawResourceFd() solo abre recursos sin comprimir (fallaba con
     * NotFoundException), así que se copia una vez a la caché y se reproduce desde ahí.
     */
    private File audioFile() throws IOException {
        File f = new File(app.getCacheDir(), "exceso_velocidad.ogg");
        if (f.length() > 0) return f;
        File tmp = new File(app.getCacheDir(), "exceso_velocidad.tmp");
        try (InputStream in = app.getResources().openRawResource(R.raw.exceso_velocidad);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        }
        if (!tmp.renameTo(f)) throw new IOException("no se pudo guardar el audio en la caché");
        return f;
    }

    private static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    private void stopPlayer() {
        if (player != null) {
            try { player.release(); } catch (RuntimeException ignored) {}
            player = null;
        }
    }

    private void beep() {
        stopPlayer();
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 700);
            main.postDelayed(tg::release, 900);
        } catch (RuntimeException e) {
            Diag.warn("gps", "pitido", e);
        }
        main.postDelayed(this::endDucking, 1500);
    }

    // ------------------------------------------------------------------ atenuar lo que suena y subir el volumen

    private void beginDucking() {
        main.removeCallbacks(endNow);
        main.postDelayed(endNow, MAX_WARNING_MS); // pase lo que pase, se restablece
        if (active || audio == null) return;
        active = true;
        try {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes())
                    .setOnAudioFocusChangeListener(l -> {})
                    .build();
            int r = audio.requestAudioFocus(focus);
            Diag.log("gps", "enfoque de audio para el aviso: " + (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "concedido" : "no concedido (" + r + ")"));
        } catch (RuntimeException e) {
            Diag.warn("gps", "enfoque de audio", e);
        }
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int want = Math.round(max * MIN_VOLUME_PCT / 100f);
            if (cur < want) {
                savedVolume = cur;
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, want, 0);
            }
        } catch (RuntimeException e) {
            Diag.warn("gps", "subir volumen", e);
        }
    }

    private final Runnable endNow = this::endDucking;

    private void endDucking() {
        main.removeCallbacks(endNow);
        if (!active) return;
        active = false;
        stopPlayer();
        if (savedVolume >= 0 && audio != null) {
            try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0); } catch (RuntimeException ignored) {}
        }
        savedVolume = -1;
        if (focus != null && audio != null) {
            try { audio.abandonAudioFocusRequest(focus); } catch (RuntimeException ignored) {}
        }
        focus = null;
    }

    public String describe() {
        return (limit.enabled() ? "límite " + limit.limit() + " km/h" : "límite desactivado") + " · " + warnings
                + " avisos · audio grabado (es-MX): " + lastResult;
    }
}
