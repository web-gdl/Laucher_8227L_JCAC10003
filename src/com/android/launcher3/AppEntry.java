package com.android.launcher3;

import android.content.ComponentName;

/** Una actividad lanzable. Inmutable; el icono vive aparte, en la caché. */
public final class AppEntry {
    public final ComponentName component;
    public final String packageName;
    public final String label;
    /** Clave estable: ComponentName.flattenToString(). */
    public final String key;

    public AppEntry(ComponentName component, String label) {
        this.component = component;
        this.packageName = component.getPackageName();
        this.label = label;
        this.key = component.flattenToString();
    }

    @Override public boolean equals(Object o) {
        return o instanceof AppEntry && ((AppEntry) o).key.equals(key);
    }

    @Override public int hashCode() { return key.hashCode(); }

    @Override public String toString() { return label + " [" + key + "]"; }
}
