package com.android.launcher3.car;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Consumo de gasolina por el método del tanque lleno. El estéreo no lee el auto, así que el consumo sale de
 * las cargas que registra el usuario: lectura del odómetro (km), litros y costo de cada una. Java puro.
 *
 * Promedio = km recorridos entre la primera y la última carga / litros de todas las cargas menos la primera
 * (la primera solo fija el punto de partida). Se supone que cada carga llena el tanque.
 */
public final class FuelLog {
    public static final int MAX_ENTRIES = 30;

    public static final class Entry {
        public final double km, liters, cost;
        Entry(double km, double liters, double cost) { this.km = km; this.liters = liters; this.cost = cost; }
    }

    private final List<Entry> entries = new ArrayList<>();

    /** Formato guardado: «km:litros:costo;km:litros:costo». */
    public static FuelLog parse(String s) {
        FuelLog f = new FuelLog();
        if (s == null || s.isEmpty()) return f;
        for (String part : s.split(";")) {
            String[] p = part.split(":");
            if (p.length != 3) continue;
            try { f.entries.add(new Entry(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]))); }
            catch (NumberFormatException ignored) { }
        }
        return f;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (sb.length() > 0) sb.append(';');
            sb.append(String.format(Locale.US, "%.1f:%.2f:%.2f", e.km, e.liters, e.cost));
        }
        return sb.toString();
    }

    /** @return false si los datos no sirven (litros ≤ 0 o un kilometraje que no avanza). */
    public boolean add(double km, double liters, double cost) {
        if (!(liters > 0 && liters < 500) || km < 0 || cost < 0) return false;
        if (!entries.isEmpty() && km <= entries.get(entries.size() - 1).km) return false;
        entries.add(new Entry(km, liters, cost));
        while (entries.size() > MAX_ENTRIES) entries.remove(0);
        return true;
    }

    public int size() { return entries.size(); }

    public void clear() { entries.clear(); }

    public void removeLast() { if (!entries.isEmpty()) entries.remove(entries.size() - 1); }

    public double lastKm() { return entries.isEmpty() ? -1 : entries.get(entries.size() - 1).km; }

    /** Litros de la última carga, o -1. */
    public double lastLiters() { return entries.isEmpty() ? -1 : entries.get(entries.size() - 1).liters; }

    /** km por litro de todo el historial, o -1 si aún no hay dos cargas. */
    public double avgKmPerL() {
        if (entries.size() < 2) return -1;
        double liters = 0;
        for (int i = 1; i < entries.size(); i++) liters += entries.get(i).liters;
        return (entries.get(entries.size() - 1).km - entries.get(0).km) / liters;
    }

    /** Costo por km de la última carga, o -1 si no hay costo o dos cargas. */
    public double lastCostPerKm() {
        int n = entries.size();
        if (n < 2) return -1;
        Entry last = entries.get(n - 1), prev = entries.get(n - 2);
        if (last.cost <= 0) return -1;
        return last.cost / (last.km - prev.km);
    }

    /** Autonomía con el tanque lleno, o -1. */
    public double rangeKm(double tankLiters) {
        double a = avgKmPerL();
        return a < 0 || tankLiters <= 0 ? -1 : a * tankLiters;
    }
}
