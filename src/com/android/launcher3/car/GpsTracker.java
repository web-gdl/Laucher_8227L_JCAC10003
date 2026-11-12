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

    public enum State { NO_PERMISSION, DISABLED, SEARCHING, OK }

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

    public GpsTracker(Context context) {
        app = context.getApplicationContext();
        store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        trip = new Trip(Double.longBitsToDouble(store.getLong("trip", 0)), Double.longBitsToDouble(store.getLong("total", 0)));
        updateIdleState();
    }

    public boolean hasPermission() {
        return app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------------------------------------------ ciclo de vida (lo llama TripService)

    public void start() {
        if (running) return;
        if (!hasPermission()) { setState(State.NO_PERMISSION); return; }
        if (lm == null) lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) { setState(State.DISABLED); return; }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper());
            running = true;
            trip.breakSegment();
            setState(lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? State.SEARCHING : State.DISABLED);
            Diag.log("gps", "inicio, estado " + state);
            main.postDelayed(staleCheck, Trip.STALE_MS);
        } catch (SecurityException | IllegalArgumentException e) {
            Diag.warn("gps", "requestLocationUpdates", e);
            setState(hasPermission() ? State.DISABLED : State.NO_PERMISSION);
        }
    }

    public void stop() {
        main.removeCallbacks(staleCheck);
        if (running && lm != null) {
            try { lm.removeUpdates(locationListener); } catch (RuntimeException ignored) {}
        }
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
        state = hasPermission() ? State.SEARCHING : State.NO_PERMISSION;
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    // ------------------------------------------------------------------ GPS

    private final LocationListener locationListener = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            long now = l.getElapsedRealtimeNanos() > 0 ? l.getElapsedRealtimeNanos() : SystemClock.elapsedRealtimeNanos();
            lastAccuracy = l.hasAccuracy() ? l.getAccuracy() : -1f;
            Bundle extras = l.getExtras();
            if (extras != null && extras.containsKey("satellites")) satellites = extras.getInt("satellites", -1);
            double added = trip.onFix(l.getLatitude(), l.getLongitude(), l.hasAccuracy() ? l.getAccuracy() : 999f,
                    l.hasSpeed(), l.getSpeed(), l.hasBearing(), l.getBearing(), now);
            unsavedM += added;
            if (unsavedM > 0 && SystemClock.elapsedRealtime() - lastSave > SAVE_EVERY_MS) save();
            state = State.OK;
            main.removeCallbacks(staleCheck);
            main.postDelayed(staleCheck, Trip.STALE_MS + 200);
            notifyChanged();
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override public void onProviderEnabled(String provider) {
            Diag.log("gps", "GPS activado");
            setState(State.SEARCHING);
        }

        @Override public void onProviderDisabled(String provider) {
            Diag.log("gps", "GPS desactivado");
            trip.breakSegment();
            setState(State.DISABLED);
        }
    };

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
                .putLong("total", Double.doubleToRawLongBits(trip.totalM())).apply();
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
        if (on) TripService.start(app);
        else { save(); TripService.stop(app); }
    }

    @Override public void onMediaChanged(MediaState s) {}

    @Override public void onServicesChanged() {}

    @Override public void onCcdChanged(int status) {}
}
