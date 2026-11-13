import com.android.launcher3.car.FuelLog;
import com.android.launcher3.car.SpeedLimit;
import com.android.launcher3.car.Trip;

/** Recorridos simulados para el odómetro (se ejecuta con test/run.sh, sin Android). */
public class TripTest {
    static int fails = 0;
    static java.util.Random rnd = new java.util.Random(42);
    static final double M_PER_DEG_LAT = 111_195.0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "ok    " : "FALLA ") + name + "  " + detail);
        if (!ok) fails++;
    }

    /** Recta hacia el norte a v km/h durante s segundos, una posición por segundo, con ruido de ±jitter m. */
    static long drive(Trip t, double[] pos, long nanos, double kmh, int secs, double jitter, boolean hasSpeed, float acc) {
        for (int i = 0; i < secs; i++) {
            nanos += 1_000_000_000L;
            pos[0] += kmh / 3.6 / M_PER_DEG_LAT;
            double jl = (rnd.nextDouble() * 2 - 1) * jitter / M_PER_DEG_LAT;
            double jo = (rnd.nextDouble() * 2 - 1) * jitter / (M_PER_DEG_LAT * Math.cos(Math.toRadians(pos[0])));
            t.onFix(pos[0] + jl, pos[1] + jo, acc, hasSpeed, (float) (kmh / 3.6), hasSpeed && kmh > 0, 0f, nanos);
        }
        return nanos;
    }

    public static void main(String[] a) {
        double[] p = {20.6597, -103.3496}; // Guadalajara
        long n = 1_000_000_000L;

        Trip t = new Trip(0, 0);
        n = drive(t, p, n, 0, 300, 8, true, 10);
        check("parado 5 min con ruido ±8 m (con velocidad del GPS)", t.tripM() < 1, String.format("%.1f m", t.tripM()));

        Trip t2 = new Trip(0, 0);
        n = drive(t2, p, n, 0, 300, 8, false, 10);
        check("parado 5 min con ruido ±8 m (sin velocidad del GPS)", t2.tripM() < 30, String.format("%.1f m", t2.tripM()));

        Trip t3 = new Trip(0, 1000);
        n = drive(t3, p, n, 50, 120, 3, true, 8);
        double exp = 50 / 3.6 * 120;
        check("120 s a 50 km/h", Math.abs(t3.tripM() - exp) / exp < 0.04, String.format("%.0f m (esperado %.0f)", t3.tripM(), exp));
        check("total suma lo mismo", Math.abs(t3.totalM() - 1000 - t3.tripM()) < 1e-6, String.format("%.0f m", t3.totalM()));
        check("velocidad mostrada ≈ 50", Math.abs(t3.speedKmh(n) - 50) < 0.5, String.format("%.1f km/h", t3.speedKmh(n)));
        check("rumbo norte en movimiento", t3.bearing() == 0f && Trip.cardinal(t3.bearing()).equals("N"), "" + t3.bearing());

        check("tiempo en marcha ≈ 120 s", Math.abs(t3.movingMs() - 120_000) < 3000, t3.movingMs() + " ms");
        check("velocidad media ≈ 50", Math.abs(t3.avgKmh() - 50) < 3, String.format("%.1f km/h", t3.avgKmh()));
        check("máxima ≈ 50", Math.abs(t3.maxKmh() - 50) < 0.5, String.format("%.1f km/h", t3.maxKmh()));
        check("parado no cuenta como tiempo en marcha", t.movingMs() == 0, t.movingMs() + " ms");
        check("media desconocida con poco tiempo", new Trip(0, 0).avgKmh() < 0, "");
        check("altitud conocida solo si el GPS la trae", Double.isNaN(t3.altitudeM()), "" + t3.altitudeM());
        n += 1_000_000_000L;
        t3.onFix(p[0], p[1], 8, true, 14f, true, 0f, 1540.0, n);
        check("altitud 1540 m", Math.abs(t3.altitudeM() - 1540) < 1e-6, "" + t3.altitudeM());
        long mv = t3.movingMs();
        n += 30_000_000_000L; // hueco de 30 s sin posiciones: no se cuenta como marcha
        t3.onFix(p[0], p[1], 8, true, 14f, true, 0f, n);
        check("hueco sin posiciones no suma tiempo", t3.movingMs() == mv, (t3.movingMs() - mv) + " ms");
        t3.resetTrip();
        check("reiniciar viaje borra estadísticas", t3.movingMs() == 0 && t3.maxKmh() == 0, "");

        double before = t3.tripM();
        n += 1_000_000_000L;
        t3.onFix(p[0] + 2000 / M_PER_DEG_LAT, p[1], 8, true, 14f, true, 0f, n); // salto de 2 km en 1 s
        check("salto imposible descartado", t3.tripM() == before, String.format("%.0f m", t3.tripM()));
        p[0] += 2000 / M_PER_DEG_LAT;

        before = t3.tripM();
        n += 1_000_000_000L;
        t3.onFix(p[0] + 15 / M_PER_DEG_LAT, p[1], 60f, true, 14f, true, 0f, n); // precisión mala
        check("posición imprecisa (60 m) no suma", t3.tripM() == before, "");

        Trip t4 = new Trip(0, 0);
        n = drive(t4, p, n, 60, 60, 3, false, 8);
        exp = 60 / 3.6 * 60;
        check("60 s a 60 km/h sin velocidad del GPS", Math.abs(t4.tripM() - exp) / exp < 0.05, String.format("%.0f m (esperado %.0f)", t4.tripM(), exp));
        n = drive(t4, p, n, 0, 600, 6, false, 8);   // parada larga
        before = t4.tripM();
        n = drive(t4, p, n, 40, 60, 3, false, 8);
        exp = 40 / 3.6 * 60;
        check("tras parada de 10 min vuelve a contar", Math.abs(t4.tripM() - before - exp) / exp < 0.07, String.format("%.0f m (esperado %.0f)", t4.tripM() - before, exp));

        Trip t5 = new Trip(0, 0);
        n = drive(t5, p, n, 10, 180, 3, false, 8);
        exp = 10 / 3.6 * 180;
        check("3 min a 10 km/h sin velocidad del GPS (tráfico)", Math.abs(t5.tripM() - exp) / exp < 0.10, String.format("%.0f m (esperado %.0f)", t5.tripM(), exp));

                check("velocidad desconocida tras 6 s sin GPS", t4.speedKmh(n + 6_000_000_000L) < 0, "");
        check("parado: velocidad 0", drive(t4, p, n, 0, 5, 3, true, 8) > 0 && t4.speedKmh(n + 5_000_000_000L) == 0f, "" + t4.speedKmh(n + 5_000_000_000L));

        float bear = t3.bearing();
        drive(t3, p, n, 0, 30, 8, true, 8);
        check("parado conserva el último rumbo", t3.bearing() == bear, "" + t3.bearing());

        String[][] c = {{"0", "N"}, {"44", "NE"}, {"45", "NE"}, {"90", "E"}, {"200", "S"}, {"250", "O"}, {"300", "NO"}, {"350", "N"}, {"-1", "—"}};
        boolean ok = true;
        for (String[] x : c) ok &= Trip.cardinal(Float.parseFloat(x[0])).equals(x[1]);
        check("puntos cardinales", ok, "");

        t3.resetTrip();
        check("reiniciar viaje deja el total", t3.tripM() == 0 && t3.totalM() > 1000, "");

        // Límite de velocidad
        SpeedLimit sl = new SpeedLimit(110);
        boolean w = false; long ms = 0;
        for (int i = 0; i < 10; i++) { w |= sl.onSpeed(i == 5 ? 140 : 100, ms); ms += 1000; }
        check("límite: un pico aislado del GPS no avisa", !w, "");
        int count = 0; ms = 100_000;
        for (int i = 0; i <= 70; i++) { if (sl.onSpeed(115, ms)) count++; ms += 1000; }
        check("límite: 70 s por encima avisa a los 2 s y repite cada 30 s (3 avisos)", count == 3, "" + count);
        boolean again = false;
        sl.onSpeed(109, ms); ms += 1000;            // apenas por debajo: no se rearma
        for (int i = 0; i < 3; i++) { again |= sl.onSpeed(115, ms); ms += 1000; }
        check("límite: oscilar junto al límite no repite antes de 30 s", !again, "");
        sl.onSpeed(100, ms); ms += 1000;            // bajó de verdad: se rearma
        boolean rearmed = false;
        for (int i = 0; i < 3; i++) { rearmed |= sl.onSpeed(115, ms); ms += 1000; }
        check("límite: tras bajar avisa de nuevo al volver a excederlo", rearmed, "");
        sl.setLimit(0);
        check("límite desactivado no avisa", !sl.onSpeed(200, ms) && !sl.onSpeed(200, ms + 5000), "");
        check("velocidad desconocida no avisa", !new SpeedLimit(50).onSpeed(-1, 0), "");

        FuelLog f = new FuelLog();
        check("combustible: sin cargas no hay promedio", f.avgKmPerL() < 0, "");
        f.add(10000, 40, 800);
        check("combustible: una carga no basta", f.avgKmPerL() < 0 && f.lastCostPerKm() < 0, "");
        f.add(10450, 38, 790);
        f.add(10900, 40, 840);
        check("combustible: promedio (900 km / 78 L)", Math.abs(f.avgKmPerL() - 900.0 / 78) < 1e-9, String.format("%.2f km/L", f.avgKmPerL()));
        check("combustible: costo por km última carga", Math.abs(f.lastCostPerKm() - 840.0 / 450) < 1e-9, String.format("%.2f", f.lastCostPerKm()));
        check("combustible: autonomía 45 L", Math.abs(f.rangeKm(45) - 45 * 900.0 / 78) < 1e-9, String.format("%.0f km", f.rangeKm(45)));
        check("combustible: rechaza km que no avanza o litros 0", !f.add(10900, 30, 1) && !f.add(11000, 0, 1) && f.size() == 3, "");
        FuelLog g = FuelLog.parse(f.serialize());
        check("combustible: guardar y leer", g.size() == 3 && Math.abs(g.avgKmPerL() - f.avgKmPerL()) < 0.01, f.serialize());
        g.removeLast();
        check("combustible: quitar la última", g.size() == 2 && g.lastKm() == 10450, "");
        check("combustible: texto dañado se ignora", FuelLog.parse("x:y;1:2").size() == 0, "");

        System.out.println(fails == 0 ? "\nTodas las pruebas pasaron" : "\n" + fails + " pruebas fallaron");
        System.exit(fails == 0 ? 0 : 1);
    }
}
