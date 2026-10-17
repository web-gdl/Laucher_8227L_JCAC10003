package com.android.launcher3;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * Datos de la compilación, leídos del propio paquete instalado para que no haya que mantenerlos a mano.
 *
 * Variantes (las decide build.sh):
 *  - reemplazo: paquete com.android.launcher3, uid system, persistente; sustituye al launcher de fábrica.
 *  - prueba:    paquete com.webgdl.launcher8227, app normal; convive con el de fábrica.
 */
public final class BuildInfo {
    /** Paquete del launcher de fábrica; solo con él se heredan los privilegios de sistema. */
    public static final String REPLACEMENT_PACKAGE = "com.android.launcher3";

    private static String version;
    private static Boolean replacement;

    private BuildInfo() {}

    public static synchronized String version(Context ctx) {
        if (version == null) {
            try {
                PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                version = pi.versionName + " (" + pi.versionCode + ")";
            } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                version = "?";
            }
        }
        return version;
    }

    /** true si esta instalación sustituye al launcher de fábrica (misma identidad). */
    public static synchronized boolean isReplacement(Context ctx) {
        if (replacement == null) replacement = REPLACEMENT_PACKAGE.equals(ctx.getPackageName());
        return replacement;
    }

    public static String variant(Context ctx) { return isReplacement(ctx) ? "reemplazo" : "prueba"; }
}
