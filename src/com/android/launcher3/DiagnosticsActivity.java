package com.android.launcher3;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.jancar.JancarBridge;

import java.io.File;
import java.io.FileWriter;
import java.util.Locale;

/** Informe en texto de memoria, servicios y eventos recientes. Pensado para medir en el equipo real. */
public final class DiagnosticsActivity extends Activity {
    private TextView report;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_diag);
        report = findViewById(R.id.report);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { refresh(); } });
        findViewById(R.id.btn_reconnect).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { LauncherApp.get().bridge().reconnect(); report.postDelayed(new Runnable() { @Override public void run() { refresh(); } }, 1500); }
        });
        findViewById(R.id.btn_gc).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { LauncherApp.get().apps().trimIcons(true); System.gc(); refresh(); }
        });
        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { save(); } });
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() { report.setText(buildReport()); }

    private String buildReport() {
        LauncherApp app = LauncherApp.get();
        JancarBridge bridge = app.bridge();
        StringBuilder sb = new StringBuilder(4096);
        sb.append("Launcher nativo ").append(BuildInfo.version(this)).append(" · variante ").append(BuildInfo.variant(this)).append(' ').append(getPackageName()).append('\n');
        sb.append("Proceso activo: ").append(app.uptimeMs() / 1000).append(" s\n\n");

        sb.append("== Memoria ==\n");
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        sb.append(String.format(Locale.US, "Heap Java: %.1f MB usados / %.1f MB reservados (máx %.1f MB)\n",
                used / 1048576f, rt.totalMemory() / 1048576f, rt.maxMemory() / 1048576f));
        sb.append(String.format(Locale.US, "Heap nativo: %.1f MB\n", Debug.getNativeHeapAllocatedSize() / 1048576f));
        try {
            sb.append(String.format(Locale.US, "PSS total del proceso: %.1f MB\n", Debug.getPss() / 1024f));
        } catch (RuntimeException ignored) {}
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            sb.append(String.format(Locale.US, "Sistema: %.0f MB libres de %.0f MB%s (umbral %.0f MB)\n",
                    mi.availMem / 1048576f, mi.totalMem / 1048576f, mi.lowMemory ? " — MEMORIA BAJA" : "", mi.threshold / 1048576f));
            sb.append("Clase de memoria: ").append(am.getMemoryClass()).append(" MB (largeHeap ").append(am.getLargeMemoryClass()).append(" MB)\n");
        }
        sb.append("Caché de iconos: ").append(app.apps().getIconCacheCount()).append(" bitmaps, ")
                .append(app.apps().getIconCacheBytes() / 1024).append(" KB\n");
        sb.append("Apps lanzables: ").append(app.apps().getApps().size()).append(" (escaneos: ").append(app.apps().getRefreshCount()).append(")\n\n");

        sb.append("== Servicios com.jancar.services ==\n");
        sb.append(bridge.describeLinks()).append('\n');
        sb.append("ACC: ").append(bridge.isAccKnown() ? (bridge.isAccOn() ? "ON" : "OFF") : "desconocido")
                .append(" · cámara reversa: ").append(bridge.getCcdStatus()).append('\n');
        sb.append("ACC por broadcast: ").append(bridge.getAccBroadcast() == null ? "sin datos" : (bridge.getAccBroadcast() ? "ON" : "OFF"))
                .append(" · Settings.Global accStatus: ").append(readGlobal("accStatus")).append('\n');
        Boolean bl = bridge.isBacklightOn();
        sb.append("Retroiluminación: ").append(bl == null ? "?" : (bl ? "encendida" : "apagada")).append('\n');
        sb.append("App de memoria (servicio): ").append(String.valueOf(bridge.getMemoryAppPackage())).append('\n');
        sb.append("Último evento: ").append(bridge.getLastEvent()).append('\n');
        sb.append("Media: ").append(bridge.getMedia()).append('\n');
        sb.append("Sesiones multimedia: ").append(app.sessions().describe()).append("\n\n");

        sb.append("== Registro ==\n").append(Diag.dump());
        return sb.toString();
    }

    private String readGlobal(String key) {
        try { return String.valueOf(android.provider.Settings.Global.getInt(getContentResolver(), key)); }
        catch (android.provider.Settings.SettingNotFoundException e) { return "no existe"; }
        catch (RuntimeException e) { return "error"; }
    }

    private void save() {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            File f = new File(dir, "launcher-diag.txt");
            FileWriter w = new FileWriter(f, false);
            w.write(buildReport());
            w.close();
            Toast.makeText(this, getString(R.string.diag_saved, f.getAbsolutePath()), Toast.LENGTH_LONG).show();
            Diag.log("diag", "informe guardado en " + f.getAbsolutePath());
        } catch (Exception e) {
            Toast.makeText(this, R.string.diag_save_failed, Toast.LENGTH_SHORT).show();
            Diag.warn("diag", "guardar", e);
        }
    }
}
