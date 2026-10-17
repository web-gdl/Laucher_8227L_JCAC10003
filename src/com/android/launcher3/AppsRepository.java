package com.android.launcher3;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lista de aplicaciones lanzables + caché de iconos reducidos.
 *
 * Reglas:
 *  - PackageManager y decodificación de iconos SIEMPRE en el hilo de fondo único "launcher-bg".
 *  - Los iconos se rasterizan al tamaño de celda (p. ej. 72 px) y viven en una LruCache acotada;
 *    nunca se guarda el Drawable original de otra app.
 *  - Un cambio de paquete provoca un refresco de la lista (barato) y expulsa solo los iconos de ese paquete.
 */
public final class AppsRepository {

    public interface Listener { void onAppsChanged(List<AppEntry> apps); }

    private static final int ICON_CACHE_BYTES = 3 * 1024 * 1024; // ~3 MB: 60 iconos de 72 px no llegan a 1,3 MB

    private final Context app;
    private final PackageManager pm;
    private final Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final LruCache<String, Bitmap> icons = new LruCache<String, Bitmap>(ICON_CACHE_BYTES) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };

    private volatile List<AppEntry> apps = Collections.emptyList();
    private boolean refreshPending;
    private int refreshCount;

    public AppsRepository(Context context) {
        app = context.getApplicationContext();
        pm = app.getPackageManager();
        HandlerThread t = new HandlerThread("launcher-bg", Process.THREAD_PRIORITY_BACKGROUND);
        t.start();
        bg = new Handler(t.getLooper());

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        app.registerReceiver(packageReceiver, f);
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String pkg = i.getData() != null ? i.getData().getSchemeSpecificPart() : null;
            Diag.log("apps", i.getAction() + " " + pkg);
            if (pkg != null) evictPackage(pkg);
            // ADDED+REPLACED llegan seguidos: un solo refresco diferido
            refresh();
        }
    };

    // ------------------------------------------------------------------ lista

    public List<AppEntry> getApps() { return apps; }

    public int getRefreshCount() { return refreshCount; }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    /** Coalesce: varias peticiones seguidas producen un único escaneo. */
    public void refresh() {
        if (refreshPending) return;
        refreshPending = true;
        main.postDelayed(new Runnable() {
            @Override public void run() {
                refreshPending = false;
                bg.post(scanRunnable);
            }
        }, 300);
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override public void run() {
            long t0 = System.nanoTime();
            final List<AppEntry> list = scan();
            refreshCount++;
            Diag.log("apps", list.size() + " apps en " + (System.nanoTime() - t0) / 1000000 + " ms");
            main.post(new Runnable() {
                @Override public void run() {
                    apps = list;
                    for (Listener l : new ArrayList<>(listeners)) l.onAppsChanged(list);
                }
            });
        }
    };

    private List<AppEntry> scan() {
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris;
        try {
            ris = pm.queryIntentActivities(probe, 0);
        } catch (RuntimeException e) {
            Diag.warn("apps", "queryIntentActivities", e);
            return apps; // conserva la lista anterior
        }
        String self = app.getPackageName();
        List<AppEntry> out = new ArrayList<>(ris.size());
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo == null || self.equals(ri.activityInfo.packageName)) continue;
            CharSequence label = null;
            try { label = ri.loadLabel(pm); } catch (RuntimeException ignored) {}
            String text = label != null ? label.toString().trim() : ri.activityInfo.packageName;
            out.add(new AppEntry(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), text));
        }
        final Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) { return collator.compare(a.label, b.label); }
        });
        return out;
    }

    /** Resuelve una clave de favorito: "pkg:<paquete>" (primera actividad del paquete) o ComponentName aplanado. */
    public AppEntry resolve(String key) {
        List<AppEntry> cur = apps;
        if (key.startsWith("pkg:")) {
            String pkg = key.substring(4);
            for (AppEntry e : cur) if (e.packageName.equals(pkg)) return e;
            return null;
        }
        for (AppEntry e : cur) if (e.key.equals(key)) return e;
        return null;
    }

    public AppEntry findByPackage(String pkg) {
        for (AppEntry e : apps) if (e.packageName.equals(pkg)) return e;
        return null;
    }

    // ------------------------------------------------------------------ iconos

    public int getIconCacheBytes() { return icons.size(); }

    public int getIconCacheCount() { return icons.snapshot().size(); }

    public void trimIcons(boolean all) {
        if (all) icons.evictAll(); else icons.trimToSize(icons.size() / 2);
    }

    private void evictPackage(String pkg) {
        for (String k : icons.snapshot().keySet()) {
            if (k.startsWith(pkg + "/")) icons.remove(k);
        }
    }

    /**
     * Pone el icono en el ImageView: inmediato si está en caché; si no, lo rasteriza en segundo plano
     * y lo aplica solo si la celda sigue mostrando la misma app (tag).
     */
    public void bindIcon(final AppEntry entry, final ImageView target, final int sizePx) {
        final String key = entry.key + "@" + sizePx;
        target.setTag(key);
        Bitmap cached = icons.get(key);
        if (cached != null) { target.setImageBitmap(cached); return; }
        target.setImageDrawable(null);
        bg.post(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = renderIcon(entry, sizePx);
                if (bmp == null) return;
                icons.put(key, bmp);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (key.equals(target.getTag())) target.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    private Bitmap renderIcon(AppEntry entry, int sizePx) {
        Drawable d;
        try {
            d = pm.getActivityIcon(entry.component);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            try { d = pm.getDefaultActivityIcon(); } catch (RuntimeException e2) { return null; }
        }
        if (d == null) return null;
        try {
            if (d instanceof BitmapDrawable) {
                Bitmap src = ((BitmapDrawable) d).getBitmap();
                if (src != null && src.getWidth() == sizePx && src.getHeight() == sizePx) return src;
            }
            Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            d.setBounds(0, 0, sizePx, sizePx);
            d.draw(c);
            return out;
        } catch (RuntimeException | OutOfMemoryError e) {
            Diag.warn("apps", "icono " + entry.packageName, e);
            return null;
        }
    }

    // ------------------------------------------------------------------ lanzar

    public void launch(Context ctx, AppEntry entry) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(entry.component)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
            Diag.log("apps", "abrir " + entry.key);
        } catch (ActivityNotFoundException | SecurityException e) {
            Diag.warn("apps", "no se pudo abrir " + entry.key, e);
            Toast.makeText(ctx, entry.label, Toast.LENGTH_SHORT).show();
            refresh();
        }
    }

    public boolean launchPackage(Context ctx, String pkg) {
        Intent i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { ctx.startActivity(i); return true; }
        catch (ActivityNotFoundException | SecurityException e) { Diag.warn("apps", "no se pudo abrir " + pkg, e); return false; }
    }
}
