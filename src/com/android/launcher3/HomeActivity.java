package com.android.launcher3;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.jancar.JancarBridge;
import com.android.launcher3.jancar.MediaState;
import com.android.launcher3.media.MediaMemory;
import com.android.launcher3.media.SessionProbe;
import com.android.launcher3.ui.MediaWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Única pantalla: inicio «nocturno» (música al centro + dock de favoritos) y el panel de todas las
 * apps, que es una vista superpuesta dentro de la misma Activity (sin segunda Activity ni animaciones).
 *
 * La hora y el estado (Wi-Fi, Bluetooth, GPS, USB) no se dibujan: ya los muestra la barra del sistema.
 */
public final class HomeActivity extends Activity implements AppsRepository.Listener, JancarBridge.Listener, SessionProbe.Listener {

    private Prefs prefs;
    private AppsRepository apps;
    private JancarBridge bridge;
    private SessionProbe sessions;
    private MediaMemory memory;

    private View homeRoot;
    private View appsPanel;
    private LinearLayout dock;
    private GridView appsGrid;
    private TextView appsCount;
    private ImageView btnToggleHidden;
    private MediaWidget mediaWidget;
    private AppsAdapter appsAdapter;

    private List<String> favKeys;
    private Set<String> hidden;
    private boolean showHidden;
    private int ringIconPx;
    private int appIconPx;
    private boolean globalsWritten;
    private boolean resumed;
    // Avance de la barra de progreso de apps de terceros: solo mientras suena y el inicio está visible
    private final Handler ticker = new Handler();
    private final Runnable tick = new Runnable() { @Override public void run() { renderMedia(); } };

    // ------------------------------------------------------------------ ciclo de vida

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LauncherApp app = LauncherApp.get();
        prefs = app.prefs();
        apps = app.apps();
        bridge = app.bridge();
        sessions = app.sessions();
        memory = app.memory();
        favKeys = new ArrayList<>(prefs.getFavorites());
        hidden = prefs.getHidden();
        showHidden = prefs.getShowHidden();
        ringIconPx = getResources().getDimensionPixelSize(R.dimen.ring_icon);
        appIconPx = getResources().getDimensionPixelSize(R.dimen.app_icon);

        setContentView(R.layout.activity_home);
        homeRoot = findViewById(R.id.home_root);
        appsPanel = findViewById(R.id.apps_panel);
        dock = findViewById(R.id.dock);
        appsGrid = findViewById(R.id.apps_grid);
        appsCount = findViewById(R.id.apps_count);
        btnToggleHidden = findViewById(R.id.btn_toggle_hidden);

        View mediaCard = findViewById(R.id.media_card);
        mediaWidget = new MediaWidget(homeRoot, (ImageView) findViewById(R.id.backdrop), prefs);

        buildDock();
        appsAdapter = new AppsAdapter();
        appsGrid.setAdapter(appsAdapter);
        appsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                AppEntry e = appsAdapter.getItem(pos);
                if (e != null) { apps.launch(HomeActivity.this, e); }
            }
        });
        appsGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                AppEntry e = appsAdapter.getItem(pos);
                if (e != null) showAppMenu(e, false);
                return true;
            }
        });

        mediaCard.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openMediaApp(); } });
        mediaCard.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { chooseMediaApp(); return true; } });
        findViewById(R.id.media_play).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // Sin fuente activa pero con última canción: reabrir su app (ella sigue donde iba).
                if (!bridge.getMedia().hasContent() && memory.hasLast() && memory.openApp("toque play")) return;
                bridge.mediaPlayPause();
            }
        });
        findViewById(R.id.media_next).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { bridge.mediaNext(); } });
        findViewById(R.id.media_prev).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { bridge.mediaPrev(); } });

        findViewById(R.id.btn_screen_off).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!bridge.screenOff()) Toast.makeText(HomeActivity.this, R.string.media_service_offline, Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openSettings(); } });
        findViewById(R.id.btn_diag).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(HomeActivity.this, DiagnosticsActivity.class)); }
        });
        findViewById(R.id.btn_apps_back).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showHome(); } });
        btnToggleHidden.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showHidden = !showHidden;
                prefs.setShowHidden(showHidden);
                refreshApps();
            }
        });
        Diag.log("home", "onCreate");
    }

    @Override protected void onStart() {
        super.onStart();
        apps.addListener(this);
        bridge.addListener(this);
        sessions.addListener(this);
        onAppsChanged(apps.getApps());
        renderMedia();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        bridge.setHomeVisible(true);
        bridge.requestMediaState();
        writeSystemUiGlobals();
        renderMedia();
    }

    @Override protected void onPause() {
        resumed = false;
        ticker.removeCallbacks(tick);
        bridge.setHomeVisible(false);
        super.onPause();
    }

    @Override protected void onStop() {
        apps.removeListener(this);
        bridge.removeListener(this);
        sessions.removeListener(this);
        super.onStop();
    }

    @Override protected void onDestroy() {
        mediaWidget.release();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showHome(); // tecla HOME estando en "todas las apps"
    }

    @Override public void onBackPressed() {
        if (appsPanel.getVisibility() == View.VISIBLE) showHome();
        // En la pantalla de inicio, "atrás" no hace nada (es la HOME).
    }

    /** Valores que SystemUI de Jancar consulta; el launcher de fábrica escribe 0 en ambos para los temas normales. */
    private void writeSystemUiGlobals() {
        if (globalsWritten) return;
        globalsWritten = true;
        try {
            Settings.Global.putInt(getContentResolver(), "jancar_sysui_home_enter", 0);
            Settings.Global.putInt(getContentResolver(), "LauncherActivityView", 0);
        } catch (SecurityException e) {
            Diag.warn("home", "Settings.Global (¿sin uid system?)", e);
        }
    }

    // ------------------------------------------------------------------ navegación interna

    private void showApps() {
        refreshApps();
        appsPanel.setVisibility(View.VISIBLE);
        homeRoot.setVisibility(View.INVISIBLE);
    }

    private void showHome() {
        if (appsPanel.getVisibility() != View.VISIBLE) return;
        appsPanel.setVisibility(View.GONE);
        homeRoot.setVisibility(View.VISIBLE);
    }

    private void refreshApps() {
        List<AppEntry> all = apps.getApps();
        List<AppEntry> shown = new ArrayList<>(all.size());
        for (AppEntry e : all) if (showHidden || !hidden.contains(e.key)) shown.add(e);
        appsAdapter.setItems(shown);
        appsCount.setText(shown.size() + (hidden.isEmpty() ? "" : " · " + hidden.size() + " ocultas"));
        btnToggleHidden.setImageTintList(ColorStateList.valueOf(getColor(showHidden ? R.color.accent : R.color.text_dim)));
        btnToggleHidden.setVisibility(hidden.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ AppsRepository.Listener

    @Override public void onAppsChanged(List<AppEntry> list) {
        rebuildDock();
        if (appsPanel.getVisibility() == View.VISIBLE) refreshApps();
    }

    // ------------------------------------------------------------------ JancarBridge / SessionProbe

    @Override public void onMediaChanged(MediaState state) { renderMedia(); }

    @Override public void onServicesChanged() { renderMedia(); }

    @Override public void onAccChanged(boolean on) { /* el servicio apaga la pantalla; nada que hacer aquí */ }

    @Override public void onCcdChanged(int status) { /* la cámara la dibuja el sistema */ }

    @Override public void onSessionChanged(SessionProbe probe) { renderMedia(); }

    /**
     * Fuentes de terceros (tipo 20): el servicio las publica sin paquete y con estado/posición
     * poco fiables, así que se completan con la sesión multimedia de la app que suena.
     */
    private void renderMedia() {
        MediaState m = bridge.getMedia();
        if (m.isThirdParty() && sessions.hasSession()) {
            m.sourcePackage = sessions.getPackageName();
            m.fromSession = true;
            m.state = sessions.jancarState();
            m.position = sessions.positionSec();
            m.duration = sessions.durationSec();
            if ((m.title == null || m.title.isEmpty()) && !sessions.getTitle().isEmpty()) {
                m.title = sessions.getTitle();
                if (m.artist == null || m.artist.isEmpty()) m.artist = sessions.getArtist();
            }
        } else {
            m.fromSession = false;
            if (!m.isThirdParty()) m.sourcePackage = null;
        }
        if (!m.hasContent() && memory.hasLast()) mediaWidget.renderLast(memory.getTitle(), memory.getArtist());
        else mediaWidget.render(m, bridge.isMediaOnline());
        ticker.removeCallbacks(tick);
        if (resumed && m.fromSession && m.isPlaying() && m.duration > 0) ticker.postDelayed(tick, 1000);
    }

    // ------------------------------------------------------------------ dock

    private static final class Ring {
        View root; ImageView icon; TextView label;
    }

    private final Ring[] rings = new Ring[Prefs.MAX_FAVORITES];

    /** Crea una vez las 6 celdas de favoritos y la de «Todas». */
    private void buildDock() {
        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < rings.length; i++) {
            Ring r = new Ring();
            r.root = inf.inflate(R.layout.item_ring, dock, false);
            r.icon = r.root.findViewById(R.id.icon);
            r.label = r.root.findViewById(R.id.label);
            rings[i] = r;
            dock.addView(r.root);
        }
        View all = inf.inflate(R.layout.item_ring, dock, false);
        ImageView allIcon = all.findViewById(R.id.icon);
        allIcon.setImageResource(R.drawable.ic_apps);
        allIcon.setImageTintList(ColorStateList.valueOf(getColor(R.color.accent)));
        int pad = getResources().getDimensionPixelSize(R.dimen.ring_icon) / 6;
        allIcon.setPadding(pad, pad, pad, pad);
        ((TextView) all.findViewById(R.id.label)).setText(R.string.all_apps_short);
        all.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showApps(); } });
        dock.addView(all);
    }

    private void rebuildDock() {
        int n = 0;
        AppEntry[] slots = new AppEntry[rings.length];
        for (String k : favKeys) {
            AppEntry e = apps.resolve(k);
            if (e != null && n < slots.length) slots[n++] = e;
        }
        int pad = ringIconPx / 6;
        for (int i = 0; i < rings.length; i++) {
            final Ring r = rings[i];
            final AppEntry e = slots[i];
            if (e == null) {
                r.icon.setTag(null);
                r.icon.setImageResource(R.drawable.ic_add);
                r.icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.text_dim)));
                r.icon.setPadding(pad, pad, pad, pad);
                r.icon.setAlpha(1f);
                r.label.setText(R.string.empty_slot);
                r.root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showApps(); } });
                r.root.setOnLongClickListener(null);
            } else {
                r.icon.setImageTintList(null);
                r.icon.setPadding(0, 0, 0, 0);
                r.icon.setAlpha(0.9f); // icono a color, apenas atenuado para la noche
                r.label.setText(e.label);
                apps.bindIcon(e, r.icon, ringIconPx);
                r.root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { apps.launch(HomeActivity.this, e); } });
                r.root.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { showAppMenu(e, true); return true; }
                });
            }
        }
    }

    // ------------------------------------------------------------------ acciones

    private void openMediaApp() {
        String pkg;
        MediaState cur = bridge.getMedia();
        if (cur.isThirdParty() && cur.sourcePackage != null && apps.launchPackage(this, cur.sourcePackage)) return;
        if (!cur.hasContent() && memory.hasLast() && memory.openApp("toque portada")) return;
        switch (cur.type) {
            case MediaState.TYPE_A2DP: pkg = "com.jancar.bluetooth"; break;
            case MediaState.TYPE_RADIO: pkg = "com.jancar.radio"; break;
            case MediaState.TYPE_VIDEO: pkg = "com.jancar.video"; break;
            default: pkg = prefs.getMediaApp();
        }
        if (!apps.launchPackage(this, pkg) && !apps.launchPackage(this, prefs.getMediaApp())) {
            chooseMediaApp();
        }
    }

    private void chooseMediaApp() {
        final List<AppEntry> list = apps.getApps();
        if (list.isEmpty()) return;
        String[] labels = new String[list.size()];
        int checked = -1;
        String cur = prefs.getMediaApp();
        for (int i = 0; i < list.size(); i++) {
            labels[i] = list.get(i).label;
            if (checked < 0 && list.get(i).packageName.equals(cur)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_media_app)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        prefs.setMediaApp(list.get(which).packageName);
                        Diag.log("home", "app de música: " + list.get(which).packageName);
                        d.dismiss();
                    }
                })
                .show();
    }

    private void openSettings() {
        if (apps.launchPackage(this, "com.jancar.settings")) return;
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.settings, Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppMenu(final AppEntry e, final boolean fromFavorites) {
        final boolean isFav = favKeys.contains(e.key) || favKeys.contains("pkg:" + e.packageName);
        final boolean isHidden = hidden.contains(e.key);
        final List<String> items = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        items.add(getString(isFav ? R.string.remove_favorite : R.string.add_favorite));
        actions.add(new Runnable() { @Override public void run() { if (isFav) removeFavorite(e); else addFavorite(e); } });
        if (!fromFavorites) {
            items.add(getString(isHidden ? R.string.show_hidden : R.string.hide_app));
            actions.add(new Runnable() {
                @Override public void run() {
                    if (isHidden) hidden.remove(e.key); else hidden.add(e.key);
                    prefs.setHidden(hidden);
                    refreshApps();
                }
            });
        }
        items.add(getString(R.string.app_info));
        actions.add(new Runnable() {
            @Override public void run() {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ex) { Toast.makeText(HomeActivity.this, e.packageName, Toast.LENGTH_SHORT).show(); }
            }
        });
        items.add(getString(R.string.uninstall));
        actions.add(new Runnable() {
            @Override public void run() {
                try {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ex) { Toast.makeText(HomeActivity.this, e.packageName, Toast.LENGTH_SHORT).show(); }
            }
        });
        new AlertDialog.Builder(this)
                .setTitle(e.label)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { actions.get(which).run(); }
                })
                .show();
    }

    private void addFavorite(AppEntry e) {
        // Normaliza las claves "pkg:" heredadas de los valores por defecto
        List<String> resolved = new ArrayList<>();
        for (String k : favKeys) { AppEntry r = apps.resolve(k); if (r != null) resolved.add(r.key); }
        if (resolved.contains(e.key)) return;
        if (resolved.size() >= Prefs.MAX_FAVORITES) {
            Toast.makeText(this, getString(R.string.favorites_full, Prefs.MAX_FAVORITES), Toast.LENGTH_SHORT).show();
            return;
        }
        resolved.add(e.key);
        favKeys = resolved;
        prefs.setFavorites(favKeys);
        rebuildDock();
        Toast.makeText(this, e.label, Toast.LENGTH_SHORT).show();
    }

    private void removeFavorite(AppEntry e) {
        List<String> resolved = new ArrayList<>();
        for (String k : favKeys) { AppEntry r = apps.resolve(k); if (r != null && !r.key.equals(e.key)) resolved.add(r.key); }
        favKeys = resolved;
        prefs.setFavorites(favKeys);
        rebuildDock();
    }

    // ------------------------------------------------------------------ adaptador del panel de apps

    private static final class Holder {
        ImageView icon; TextView label; AppEntry entry;
    }

    private final class AppsAdapter extends BaseAdapter {
        private List<AppEntry> items = new ArrayList<>();

        void setItems(List<AppEntry> list) { items = list; notifyDataSetChanged(); }

        @Override public int getCount() { return items.size(); }
        @Override public AppEntry getItem(int pos) { return pos >= 0 && pos < items.size() ? items.get(pos) : null; }
        @Override public long getItemId(int pos) { return pos; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            Holder h;
            if (convert == null) {
                convert = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
                h = new Holder();
                h.icon = convert.findViewById(R.id.icon);
                h.label = convert.findViewById(R.id.label);
                convert.setTag(h);
            } else {
                h = (Holder) convert.getTag();
            }
            AppEntry e = items.get(pos);
            h.entry = e;
            h.label.setText(e.label);
            h.label.setAlpha(hidden.contains(e.key) ? 0.45f : 1f);
            h.icon.setAlpha(hidden.contains(e.key) ? 0.45f : 1f);
            apps.bindIcon(e, h.icon, appIconPx);
            return convert;
        }
    }
}
