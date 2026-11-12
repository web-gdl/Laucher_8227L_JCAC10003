package com.android.launcher3.car;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import com.android.launcher3.Diag;
import com.android.launcher3.LauncherApp;

/**
 * Pide el permiso de ubicación en una tarea propia, fuera de la pantalla de inicio.
 *
 * 3.5.0/3.5.1 lo pedían desde la propia HOME al abrirse; en el alps 8227L eso dejaba el inicio sin abrir tras elegirlo
 * en «Abrir con… una vez / siempre» (la ventana del permiso se lanza desde la pila HOME). Ahora solo se pide cuando el
 * usuario toca la tarjeta de instrumentos, y desde aquí.
 */
public final class PermissionActivity extends Activity {

    private static final int REQ = 1;

    public static void open(Activity from) {
        try {
            from.startActivity(new Intent(from, PermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Diag.warn("gps", "abrir PermissionActivity", e);
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { done(true); return; }
        if (b != null) return; // recreada mientras el diálogo sigue abierto
        try {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ);
        } catch (RuntimeException e) {
            Diag.warn("gps", "requestPermissions", e);
            openAppSettings();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        Diag.log("gps", "permiso de ubicación " + (ok ? "concedido" : "denegado"));
        // «No volver a preguntar»: el sistema ya no muestra el diálogo; solo queda la pantalla de la app en Ajustes
        if (!ok && results.length > 0 && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) openAppSettings();
        done(ok);
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Diag.warn("gps", "abrir ajustes de la app", e);
        }
    }

    private void done(boolean granted) {
        LauncherApp app = LauncherApp.get();
        app.gps().recheck();
        app.gps().sync();
        finish();
    }
}
