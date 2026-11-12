package com.android.launcher3.car;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;

import com.android.launcher3.Diag;
import com.android.launcher3.R;

import java.util.Locale;

/**
 * Aviso de voz por exceso de velocidad («Está excediendo el límite de velocidad»).
 * Usa la voz del sistema (TextToSpeech) por el canal de navegación; si el estéreo no tiene voz en el idioma,
 * suena un pitido. El motor de voz se abre solo al primer aviso y se cierra tras unos minutos sin usarse.
 */
public final class SpeedAlert implements GpsTracker.Listener {

    private static final String KEY = "speed_limit";
    private static final long RELEASE_AFTER_MS = 5 * 60_000;

    private final Context app;
    private final SharedPreferences store;
    private final SpeedLimit limit;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ttsReady, ttsFailed, pending;
    private int warnings;

    public SpeedAlert(Context context) {
        app = context.getApplicationContext();
        store = app.getSharedPreferences("odometer", Context.MODE_PRIVATE);
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

    /** Dice el aviso (también lo usa el botón «Probar»). */
    public void warn() {
        main.removeCallbacks(release);
        main.postDelayed(release, RELEASE_AFTER_MS);
        if (ttsFailed) { beep(); return; }
        if (ttsReady) { speak(); return; }
        pending = true;
        if (tts == null) {
            try {
                tts = new TextToSpeech(app, status -> {
                    if (status == TextToSpeech.SUCCESS && pickLanguage()) {
                        ttsReady = true;
                        tts.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                        if (pending) speak();
                    } else {
                        Diag.log("gps", "sin voz del sistema (estado " + status + "): se usará un pitido");
                        ttsFailed = true;
                        if (pending) beep();
                    }
                    pending = false;
                });
            } catch (RuntimeException e) {
                Diag.warn("gps", "TextToSpeech", e);
                ttsFailed = true;
                beep();
            }
        }
    }

    private boolean pickLanguage() {
        Locale[] options = {Locale.getDefault(), new Locale("es", "MX"), new Locale("es")};
        for (Locale l : options) {
            if (!l.getLanguage().equals(Locale.getDefault().getLanguage())) continue; // el texto está en el idioma del sistema
            int r = tts.isLanguageAvailable(l);
            if (r >= TextToSpeech.LANG_AVAILABLE) { tts.setLanguage(l); return true; }
        }
        return false;
    }

    private void speak() {
        pending = false;
        int r = tts.speak(app.getString(R.string.speed_warning_voice), TextToSpeech.QUEUE_FLUSH, null, "limite");
        if (r != TextToSpeech.SUCCESS) beep();
    }

    private void beep() {
        pending = false;
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 600);
            main.postDelayed(tg::release, 800);
        } catch (RuntimeException e) {
            Diag.warn("gps", "pitido", e);
        }
    }

    private final Runnable release = this::releaseVoice;

    private void releaseVoice() {
        if (tts != null) {
            try { tts.shutdown(); } catch (RuntimeException ignored) {}
            tts = null;
        }
        ttsReady = false;
        ttsFailed = false;
    }

    public String describe() {
        return (limit.enabled() ? "límite " + limit.limit() + " km/h" : "límite desactivado") + " · " + warnings + " avisos"
                + (ttsReady ? " · voz lista" : ttsFailed ? " · sin voz (pitido)" : "");
    }
}
