package com.android.launcher3.car;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import com.android.launcher3.Diag;

import java.util.Locale;
import java.util.Set;

/**
 * Aviso de voz por exceso de velocidad: «Está excediendo el límite de velocidad», en español, con volumen alto.
 *
 * Mientras suena el aviso:
 *  - la música o el video de cualquier app que respete el enfoque de audio se atenúan (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
 *    Android 8 atenúa solo a las apps que no gestionan el enfoque) y vuelven a su volumen al terminar;
 *  - el volumen multimedia sube al menos al {@link #MIN_VOLUME_PCT} % durante el aviso y se restablece después.
 * La voz es la del sistema (TextToSpeech) en español; se prefiere una voz que se declare femenina y, si no hay ninguna
 * (Android no publica el género), se sube un poco el tono. Sin voz española instalada suena un pitido.
 */
public final class SpeedAlert implements GpsTracker.Listener {

    private static final String KEY = "speed_limit";
    private static final String TEXT = "Está excediendo el límite de velocidad";
    private static final long RELEASE_AFTER_MS = 5 * 60_000;
    private static final long MAX_WARNING_MS = 12_000;
    /** Volumen multimedia mínimo (porcentaje del máximo) durante el aviso. */
    static final int MIN_VOLUME_PCT = 85;

    private final Context app;
    private final SharedPreferences store;
    private final SpeedLimit limit;
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ttsReady, ttsFailed, pending;
    private int warnings;
    private String voiceInfo = "";

    // Estado del aviso en curso (hilo principal)
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

    /** Dice el aviso (también lo usa la prueba: pulsación larga sobre el velocímetro). */
    public void warn() {
        main.removeCallbacks(release);
        main.postDelayed(release, RELEASE_AFTER_MS);
        beginDucking();
        if (ttsFailed) { beep(); return; }
        if (ttsReady) { speak(); return; }
        pending = true;
        if (tts != null) return; // ya inicializando: hablará al terminar
        try {
            tts = new TextToSpeech(app, status -> main.post(() -> onTtsInit(status)));
        } catch (RuntimeException e) {
            Diag.warn("gps", "TextToSpeech", e);
            ttsFailed = true;
            beep();
        }
    }

    private void onTtsInit(int status) {
        if (tts == null) return;
        if (status == TextToSpeech.SUCCESS && configureVoice()) {
            ttsReady = true;
            tts.setAudioAttributes(attributes());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { main.post(() -> endDucking()); }
                @Override public void onError(String id) { main.post(() -> { beep(); }); }
            });
            if (pending) speak();
        } else {
            Diag.log("gps", "sin voz del sistema en español (estado " + status + "): se usará un pitido");
            ttsFailed = true;
            if (pending) beep();
        }
        pending = false;
    }

    /** Español (México, EE. UU., España o genérico) y, entre sus voces, la que se declare femenina. */
    private boolean configureVoice() {
        Locale chosen = null;
        for (Locale l : new Locale[]{new Locale("es", "MX"), new Locale("es", "US"), new Locale("es", "ES"), new Locale("es")}) {
            if (tts.isLanguageAvailable(l) >= TextToSpeech.LANG_AVAILABLE) { chosen = l; break; }
        }
        if (chosen == null) return false;
        tts.setLanguage(chosen);
        boolean female = false;
        try {
            Set<Voice> voices = tts.getVoices();
            Voice best = null;
            int bestScore = Integer.MIN_VALUE;
            if (voices != null) for (Voice v : voices) {
                if (!"es".equals(v.getLocale().getLanguage())) continue;
                if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                String n = v.getName().toLowerCase(Locale.ROOT);
                boolean f = n.contains("female") || n.contains("femenin") || n.contains("mujer");
                boolean m = !f && (n.contains("male") || n.contains("masculin") || n.contains("hombre"));
                int score = (f ? 20 : 0) - (m ? 20 : 0) + (v.getLocale().equals(chosen) ? 4 : 0)
                        + (v.isNetworkConnectionRequired() ? 0 : 2) + (v.getQuality() >= Voice.QUALITY_HIGH ? 1 : 0);
                if (score > bestScore) { best = v; bestScore = score; female = f; }
            }
            if (best != null) {
                tts.setVoice(best);
                voiceInfo = best.getName() + (female ? " (femenina)" : " (género no indicado)");
            }
        } catch (RuntimeException e) {
            Diag.warn("gps", "elegir voz", e);
        }
        tts.setPitch(female ? 1.0f : 1.12f);
        tts.setSpeechRate(0.95f);
        if (voiceInfo.isEmpty()) voiceInfo = chosen.toLanguageTag();
        Diag.log("gps", "voz del aviso: " + voiceInfo);
        return true;
    }

    private static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    private void speak() {
        pending = false;
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
        int r = tts.speak(TEXT, TextToSpeech.QUEUE_FLUSH, params, "limite");
        if (r != TextToSpeech.SUCCESS) beep();
    }

    private void beep() {
        pending = false;
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 700);
            main.postDelayed(tg::release, 900);
        } catch (RuntimeException e) {
            Diag.warn("gps", "pitido", e);
        }
        main.postDelayed(() -> endDucking(), 1500);
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
        if (savedVolume >= 0 && audio != null) {
            try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0); } catch (RuntimeException ignored) {}
        }
        savedVolume = -1;
        if (focus != null && audio != null) {
            try { audio.abandonAudioFocusRequest(focus); } catch (RuntimeException ignored) {}
        }
        focus = null;
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
                + (ttsReady ? " · voz lista: " + voiceInfo : ttsFailed ? " · sin voz en español (pitido)" : "");
    }
}
