package com.android.launcher3;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Preferencias del launcher. Escrituras con apply() (asíncronas), nunca fsync en el hilo principal. */
public final class Prefs {
    private static final String FILE = "launcher";
    private static final String KEY_FAVORITES = "favorites";   // componentes separados por '\n'
    private static final String KEY_HIDDEN = "hidden";         // StringSet de componentes
    private static final String KEY_MEDIA_APP = "media_app";   // paquete que abre el widget de música
    private static final String KEY_SHOW_HIDDEN = "show_hidden";
    private static final String KEY_BACKDROP = "backdrop_palette"; // colores del último fondo (ui/Backdrop)

    /** Paquete de la app de música de fábrica (IVISystem.PACKAGE_MUSIC en el SDK Jancar). */
    public static final String DEFAULT_MEDIA_APP = "com.jancar.music";
    /** Huecos del dock del inicio. */
    public static final int MAX_FAVORITES = 6;

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public List<String> getFavorites() {
        String raw = sp.getString(KEY_FAVORITES, null);
        if (raw == null) return defaultFavorites();
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\n")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    public void setFavorites(List<String> favs) {
        StringBuilder sb = new StringBuilder();
        for (String f : favs) { if (sb.length() > 0) sb.append('\n'); sb.append(f); }
        sp.edit().putString(KEY_FAVORITES, sb.toString()).apply();
    }

    /** Favoritos iniciales: apps de fábrica habituales; las que no existan se ignoran al resolver. */
    private static List<String> defaultFavorites() {
        List<String> l = new ArrayList<>();
        l.add("pkg:com.jancar.music");
        l.add("pkg:com.jancar.radio");
        l.add("pkg:com.jancar.bluetooth");
        l.add("pkg:com.jancar.video");
        l.add("pkg:com.jancar.settings");
        return Collections.unmodifiableList(l);
    }

    public Set<String> getHidden() {
        return new HashSet<>(sp.getStringSet(KEY_HIDDEN, Collections.<String>emptySet()));
    }

    public void setHidden(Set<String> hidden) {
        sp.edit().putStringSet(KEY_HIDDEN, new HashSet<>(hidden)).apply();
    }

    public boolean getShowHidden() { return sp.getBoolean(KEY_SHOW_HIDDEN, false); }

    public void setShowHidden(boolean v) { sp.edit().putBoolean(KEY_SHOW_HIDDEN, v).apply(); }

    public String getBackdrop() { return sp.getString(KEY_BACKDROP, null); }

    public void setBackdrop(String palette) { sp.edit().putString(KEY_BACKDROP, palette).apply(); }

    public String getMediaApp() { return sp.getString(KEY_MEDIA_APP, DEFAULT_MEDIA_APP); }

    public void setMediaApp(String pkg) { sp.edit().putString(KEY_MEDIA_APP, pkg).apply(); }
}
