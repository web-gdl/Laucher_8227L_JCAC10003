package com.android.launcher3.car;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

import com.android.launcher3.Diag;
import com.android.launcher3.jancar.JancarBridge;
import com.android.launcher3.jancar.MediaState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lee el GPS del estéreo (LocationManager estándar) y alimenta un {@link Trip}: velocidad, rumbo y odómetro.
 * El odómetro se guarda en preferencias cada minuto, al detenerse y al apagar el auto (ACC).
 *
 * Mientras el auto está encendido lo mantiene vivo {@link TripService} (servicio en primer plano): Android 8
 * limita el GPS de las apps en segundo plano a unas pocas posiciones por hora y el odómetro se quedaría corto.
 * Todo en el hilo principal.
 */
public final class GpsTracker implements JancarBridge.Listener {

    public enum State { OFF, NO_PERMISSION, DISABLED, STUCK, SEARCHING, OK }

    public interface Listener { void onGpsChanged(GpsTracker gps); }

    private static final String PREFS = "odometer";
    private static final long SAVE_EVERY_MS = 60_000;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final SharedPreferences store;
    private final Trip trip;
    private LocationManager lm;
    private boolean running;
    private State state = State.SEARCHING;
    private long lastSave;
    private double unsavedM;
    private float lastAccuracy = -1f;
    private int satellites = -1;
    private static final long START_TIMEOUT_MS = 6000;
    // Las llamadas al LocationManager van en un hilo propio: con el GPS del estéreo colgado pueden bloquearse,
    // y en el hilo principal eso congelaba el inicio («no responde»).
    private HandlerThread gpsThread;
    private Handler gpsHandler;
    private int callSeq, returnedSeq;
    private boolean stuck;
    private boolean homeVisible;
    private boolean accOn = true;

    public GpsTracker(Context context) {
        app = context.getApplicationContext();
        store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        trip = new Trip(Double.longBitsToDouble(store.getLong("trip", 0)), Double.longBitsToDouble(store.getLong("total", 0)));
        trip.setStats(store.getLong("trip_ms", 0), store.getFloat("trip_max", 0f));
        updateIdleState();
    }

    public boolean hasPermission() {
        return app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------------------------------------------ ciclo de vida (lo llama TripService)

    public void start() {
        if (running) return;
        if (!Guard.gpsEnabled()) { setState(State.OFF); return; }
        if (!hasPermission()) { setState(State.NO_PERMISSION); return; }
        if (stuck) { setState(State.STUCK); return; }
        if (lm == null) lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) { setState(State.DISABLED); return; }
        running = true;
        trip.breakSegment();
        Guard.begin(Guard.GPS);
        setState(State.SEARCHING);
        Diag.log("gps", "inicio");
        final int call = ++callSeq;
        worker().post(() -> {
            // Hilo «gps»: si el GPS del estéreo está colgado, esta llamada puede no volver nunca
            String error = null;
            boolean enabled = false;
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, gpsThread.getLooper());
                enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch (RuntimeException e) {
                error = e.toString();
            }
            final String err = error;
            final boolean on = enabled;
            main.post(() -> onStartReturned(call, err, on));
        });
        main.removeCallbacks(startWatchdog);
        main.postDelayed(startWatchdog, START_TIMEOUT_MS);
        main.postDelayed(staleCheck, Trip.STALE_MS);
    }

    private Handler worker() {
        if (gpsThread == null) {
            gpsThread = new HandlerThread("gps");
            gpsThread.start();
            gpsHandler = new Handler(gpsThread.getLooper());
        }
        return gpsHandler;
    }

    private void onStartReturned(int call, String error, boolean providerOn) {
        returnedSeq = call;
        main.removeCallbacks(startWatchdog);
        if (stuck) {
            stuck = false;
            Diag.log("gps", "el GPS volvió a responder");
        }
        if (call != callSeq || !running) { // se detuvo (o se reinició) mientras tanto
            if (!running) { removeUpdatesAsync(); sync(); }
            return;
        }
        if (error != null) {
            Diag.log("gps", "requestLocationUpdates: " + error);
            running = false;
            setState(hasPermission() ? State.DISABLED : State.NO_PERMISSION);
        } else if (!providerOn) {
            setState(State.DISABLED);
        }
    }

    private final Runnable startWatchdog = this::checkStartReturned;

    private void checkStartReturned() {
        if (returnedSeq == callSeq) return;
        stuck = true;
        running = false;
        main.removeCallbacks(staleCheck);
        Diag.log("gps", "el GPS del sistema no responde tras " + START_TIMEOUT_MS / 1000 + " s (¿chip colgado?)");
        setState(State.STUCK);
    }

    private void removeUpdatesAsync() {
        if (lm == null || gpsHandler == null) return;
        gpsHandler.post(() -> {
            try { lm.removeUpdates(locationListener); } catch (RuntimeException ignored) {}
        });
    }

    public void stop() {
        if (!running) { recheck(); return; }
        main.removeCallbacks(staleCheck);
        main.removeCallbacks(startWatchdog);
        removeUpdatesAsync();
        running = false;
        trip.breakSegment();
        save();
        updateIdleState();
        Diag.log("gps", "detenido, total " + Math.round(trip.totalM()) + " m");
        notifyChanged();
    }

    /** Tras conceder (o quitar) el permiso fuera del inicio: actualiza el estado mostrado. */
    public void recheck() {
        if (running) return;
        State before = state;
        updateIdleState();
        if (state != before) notifyChanged();
    }

    private void updateIdleState() {
        state = !Guard.gpsEnabled() ? State.OFF : !hasPermission() ? State.NO_PERMISSION : stuck ? State.STUCK : State.SEARCHING;
    }

    /** El inicio está (o deja de estar) a la vista. */
    public void setHomeVisible(boolean visible) {
        homeVisible = visible;
        sync();
    }

    private final Runnable syncLater = this::sync;

    /**
     * Pone en marcha o detiene la medición según lo que toca ahora:
     *  - sin activar, sin permiso o con el auto apagado: nada;
     *  - con «segundo plano» y el GPS ya comprobado: servicio en primer plano (mide con otras apps delante);
     *  - si no: GPS directo, solo mientras el inicio está visible.
     */
    public void sync() {
        main.removeCallbacks(syncLater);
        if (!Guard.gpsEnabled() || !hasPermission() || !accOn) {
            TripService.stop(app);
            if (!TripService.isActive()) stop();
            return;
        }
        if (Guard.bgEnabled() && Guard.ok(Guard.GPS)) { TripService.start(app); return; }
        TripService.stop(app);
        if (TripService.isActive()) return; // al terminar de pararse, el servicio vuelve a llamar a sync()
        if (homeVisible) {
            start();
            // primera vez: cuando el GPS quede comprobado, pasar al servicio en segundo plano
            if (Guard.bgEnabled() && !Guard.ok(Guard.GPS)) main.postDelayed(syncLater, Guard.WINDOW_MS + 2000);
        } else {
            stop();
        }
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    // ------------------------------------------------------------------ GPS

    private final LocationListener locationListener = new LocationListener() {
        // Llegan en el hilo «gps»; el estado se toca solo en el principal
        @Override public void onLocationChanged(final Location l) { main.post(() -> onFix(l)); }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override public void onProviderEnabled(String provider) {
            main.post(() -> { Diag.log("gps", "GPS activado"); if (running) setState(State.SEARCHING); });
        }

        @Override public void onProviderDisabled(String provider) {
            main.post(() -> { Diag.log("gps", "GPS desactivado"); trip.breakSegment(); if (running) setState(State.DISABLED); });
        }
    };

    private void onFix(Location l) {
        if (!running) return;
        {
            long now = l.getElapsedRealtimeNanos() > 0 ? l.getElapsedRealtimeNanos() : SystemClock.elapsedRealtimeNanos();
            lastAccuracy = l.hasAccuracy() ? l.getAccuracy() : -1f;
            Bundle extras = l.getExtras();
            if (extras != null && extras.containsKey("satellites")) satellites = extras.getInt("satellites", -1);
            double added = trip.onFix(l.getLatitude(), l.getLongitude(), l.hasAccuracy() ? l.getAccuracy() : 999f,
                    l.hasSpeed(), l.getSpeed(), l.hasBearing(), l.getBearing(),
                    l.hasAltitude() ? l.getAltitude() : Double.NaN, now);
            unsavedM += added;
            if (unsavedM > 0 && SystemClock.elapsedRealtime() - lastSave > SAVE_EVERY_MS) save();
            state = State.OK;
            main.removeCallbacks(staleCheck);
            main.postDelayed(staleCheck, Trip.STALE_MS + 200);
            notifyChanged();
        }
    }

    /** Sin posiciones durante unos segundos: «buscando» y velocidad desconocida. */
    private final Runnable staleCheck = this::checkStale;

    private void checkStale() {
        if (state == State.OK && trip.speedKmh(SystemClock.elapsedRealtimeNanos()) < 0) {
            trip.breakSegment();
            setState(State.SEARCHING);
        }
    }

    private void setState(State s) {
        state = s;
        notifyChanged();
    }

    private void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) l.onGpsChanged(this);
    }

    private void save() {
        store.edit().putLong("trip", Double.doubleToRawLongBits(trip.tripM()))
                .putLong("total", Double.doubleToRawLongBits(trip.totalM()))
                .putLong("trip_ms", trip.movingMs()).putFloat("trip_max", trip.maxKmh()).apply();
        lastSave = SystemClock.elapsedRealtime();
        unsavedM = 0;
    }

    // ------------------------------------------------------------------ lectura y ajustes

    public State state() { return state; }

    public boolean isRunning() { return running; }

    /** km/h, o -1 si no hay posición reciente. */
    public float speedKmh() { return state == State.OK ? trip.speedKmh(SystemClock.elapsedRealtimeNanos()) : -1f; }

    /** Grados (0 = norte), o -1 si aún no se sabe. */
    public float bearing() { return trip.bearing(); }

    public double tripKm() { return trip.tripM() / 1000.0; }

    public double totalKm() { return trip.totalM() / 1000.0; }

    public long movingMs() { return trip.movingMs(); }

    public float maxKmh() { return trip.maxKmh(); }

    /** km/h o -1 si aún hay poco recorrido. */
    public float avgKmh() { return trip.avgKmh(); }

    /** Metros sobre el nivel del mar o NaN. */
    public double altitudeM() { return trip.altitudeM(); }

    // Recordatorio de servicio: «cada N km»; el próximo se guarda como lectura del total
    public static final int DEFAULT_SERVICE_KM = 10000;

    public int serviceIntervalKm() { return store.getInt("service_interval", DEFAULT_SERVICE_KM); }

    /** Km que faltan para el servicio, o NaN si no hay recordatorio. Negativo = ya pasó. */
    public double serviceRemainingKm() {
        long bits = store.getLong("service_next", Long.MIN_VALUE);
        if (bits == Long.MIN_VALUE) return Double.NaN;
        return Double.longBitsToDouble(bits) - totalKm();
    }

    /** Servicio hecho hoy: el siguiente será dentro de {@code intervalKm}. */
    public void setService(int intervalKm) {
        store.edit().putInt("service_interval", intervalKm).putLong("service_last", Double.doubleToRawLongBits(totalKm()))
                .putLong("service_next", Double.doubleToRawLongBits(totalKm() + intervalKm)).apply();
        Diag.log("gps", "servicio en " + intervalKm + " km");
        notifyChanged();
    }

    /** Lectura del total en el último servicio hecho, o -1. */
    public double serviceLastKm() {
        long bits = store.getLong("service_last", Long.MIN_VALUE);
        return bits == Long.MIN_VALUE ? -1 : Double.longBitsToDouble(bits);
    }

    // Consumo de gasolina (cargas que registra el usuario, ver FuelLog) y capacidad del tanque
    public FuelLog fuel() { return FuelLog.parse(store.getString("fuel", "")); }

    public void saveFuel(FuelLog f) { store.edit().putString("fuel", f.serialize()).apply(); notifyChanged(); }

    public int tankLiters() { return store.getInt("tank", 45); }

    public void setTankLiters(int l) { store.edit().putInt("tank", l).apply(); notifyChanged(); }

    public void clearService() {
        store.edit().remove("service_next").apply();
        notifyChanged();
    }

    public void resetTrip() {
        trip.resetTrip();
        save();
        Diag.log("gps", "viaje reiniciado");
        notifyChanged();
    }

    public void setTripKm(double km) {
        trip.setTripM(km * 1000.0);
        save();
        Diag.log("gps", "viaje ajustado a " + km + " km");
        notifyChanged();
    }

    public void setTotalKm(double km) {
        trip.setTotalM(km * 1000.0);
        save();
        Diag.log("gps", "total ajustado a " + km + " km");
        notifyChanged();
    }

    public String describe() {
        return String.format(Locale.US, "%s%s · %d posiciones (%d descartadas) · precisión %s · satélites %s\n  velocidad %s · rumbo %s · viaje %.2f km · total %.1f km",
                state, running ? "" : " (detenido)", trip.fixes(), trip.rejected(),
                lastAccuracy < 0 ? "?" : Math.round(lastAccuracy) + " m", satellites < 0 ? "?" : String.valueOf(satellites),
                speedKmh() < 0 ? "--" : Math.round(speedKmh()) + " km/h",
                bearing() < 0 ? "--" : Math.round(bearing()) + "° " + Trip.cardinal(bearing()), tripKm(), totalKm());
    }

    // ------------------------------------------------------------------ ACC: medir solo con el auto encendido

    @Override public void onAccChanged(boolean on) {
        accOn = on;
        if (!on) save();
        sync();
    }

    @Override public void onMediaChanged(MediaState s) {}

    @Override public void onServicesChanged() {}

    @Override public void onCcdChanged(int status) {}
}
