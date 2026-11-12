package com.android.launcher3.car;

/**
 * Velocidad, rumbo y odómetro a partir de las posiciones del GPS. Java puro (se prueba fuera de Android).
 *
 * El estéreo no lee los datos del auto: todo sale del GPS del propio estéreo.
 *  - Velocidad: la que da el GPS (Doppler); si no la trae, distancia / tiempo entre posiciones.
 *  - Rumbo: el del GPS, solo en movimiento (parado el GPS da rumbos al azar); se conserva el último.
 *  - Distancia: suma de tramos entre posiciones buenas. Se descartan las de poca precisión, los tramos
 *    con el auto parado (el punto «baila» varios metros aunque no se mueva) y los saltos imposibles.
 */
public final class Trip {
    /** Precisión mínima (m) para contar una posición. */
    static final float MAX_ACCURACY_M = 30f;
    /** Por debajo de esto (km/h) se considera parado: no suma distancia ni cambia el rumbo. */
    static final float MOVING_KMH = 4f;
    /** Rumbo fiable a partir de esta velocidad (km/h). */
    static final float HEADING_KMH = 6f;
    /** Un tramo que implique más de esto (km/h) es un salto del GPS y se descarta. */
    static final float MAX_PLAUSIBLE_KMH = 260f;
    /** Sin velocidad del GPS: desplazamiento mínimo (m) para contar un tramo. */
    static final double MIN_STEP_M = 15;
    /** Sin velocidad del GPS: si el ancla lleva esto (ms) sin moverse, se vuelve a anclar en el punto actual. */
    static final long RESYNC_MS = 20000;
    /** Sin posición durante más de esto, la velocidad pasa a «desconocida». */
    public static final long STALE_MS = 5000;

    // Última posición aceptada como ancla para medir tramos
    private boolean hasAnchor;
    private double anchorLat, anchorLon;
    private long anchorNanos;

    private float speedKmh = -1f;   // -1 = desconocida
    private float bearing = -1f;    // -1 = desconocido
    private long lastFixNanos;
    private double tripM;
    private double totalM;
    private long fixes, rejected;

    public Trip(double tripM, double totalM) {
        this.tripM = Math.max(0, tripM);
        this.totalM = Math.max(0, totalM);
    }

    /**
     * Una posición nueva del GPS.
     * @return metros sumados al odómetro con esta posición (0 si no sumó).
     */
    public double onFix(double lat, double lon, float accuracyM, boolean hasSpeed, float speedMps,
                        boolean hasBearing, float bearingDeg, long elapsedNanos) {
        fixes++;
        lastFixNanos = elapsedNanos;
        boolean accurate = accuracyM > 0 && accuracyM <= MAX_ACCURACY_M;

        double segM = 0, segKmh = -1;
        if (hasAnchor) {
            segM = distanceM(anchorLat, anchorLon, lat, lon);
            double dt = (elapsedNanos - anchorNanos) / 1e9;
            if (dt > 0) segKmh = segM / dt * 3.6;
        }

        // Velocidad: la del GPS si la trae; si no, la del tramo
        if (hasSpeed && speedMps >= 0) speedKmh = speedMps * 3.6f;
        else if (segKmh >= 0 && accurate) speedKmh = (float) segKmh;
        if (speedKmh >= 0 && speedKmh < MOVING_KMH - 1f) speedKmh = 0f; // ruido con el auto parado

        if (!accurate) { rejected++; return 0; }

        if (hasBearing && speedKmh >= HEADING_KMH) bearing = ((bearingDeg % 360f) + 360f) % 360f;

        if (!hasAnchor) { setAnchor(lat, lon, elapsedNanos); return 0; }
        if (segKmh > MAX_PLAUSIBLE_KMH) { rejected++; setAnchor(lat, lon, elapsedNanos); return 0; }
        if (hasSpeed) {
            // Con la velocidad del GPS (Doppler, fiable): parado no suma, y el ancla sigue al punto.
            if (speedMps * 3.6f < MOVING_KMH) { setAnchor(lat, lon, elapsedNanos); return 0; }
        } else {
            // Sin ella, el «baile» del punto parece movimiento: solo cuenta un desplazamiento mayor que el
            // doble de la precisión. Mientras no lo sea, el ancla espera (así a poca velocidad los metros
            // se juntan en tramos largos), salvo tras una pausa larga, en la que se vuelve a anclar.
            double minStep = Math.max(MIN_STEP_M, 2 * accuracyM);
            long dtMs = (elapsedNanos - anchorNanos) / 1_000_000L;
            if (segM < minStep || segKmh < MOVING_KMH) {
                if (dtMs > RESYNC_MS) setAnchor(lat, lon, elapsedNanos);
                return 0;
            }
        }
        tripM += segM;
        totalM += segM;
        setAnchor(lat, lon, elapsedNanos);
        return segM;
    }

    private void setAnchor(double lat, double lon, long nanos) {
        hasAnchor = true; anchorLat = lat; anchorLon = lon; anchorNanos = nanos;
    }

    /** Sin posición reciente la velocidad es desconocida; el rumbo se conserva. */
    public float speedKmh(long nowNanos) {
        if (lastFixNanos == 0 || (nowNanos - lastFixNanos) / 1_000_000L > STALE_MS) return -1f;
        return speedKmh;
    }

    public float bearing() { return bearing; }

    public double tripM() { return tripM; }

    public double totalM() { return totalM; }

    public long fixes() { return fixes; }

    public long rejected() { return rejected; }

    public void resetTrip() { tripM = 0; }

    public void setTripM(double m) { tripM = Math.max(0, m); }

    public void setTotalM(double m) { totalM = Math.max(0, m); }

    /** Se perdió la señal o se apagó el GPS: el próximo tramo empieza de cero (no se une con un salto). */
    public void breakSegment() { hasAnchor = false; }

    /** Distancia en metros (fórmula del semiverseno, radio medio terrestre). */
    static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371008.8, p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Punto cardinal en español para un rumbo (N, NE, E, SE, S, SO, O, NO). */
    public static String cardinal(float deg) {
        if (deg < 0) return "—";
        String[] c = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        return c[(int) (((deg + 22.5f) % 360f) / 45f) % 8];
    }
}
