package com.android.launcher3;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.jancar.JancarBridge;
import com.android.launcher3.jancar.MediaState;
import com.android.launcher3.media.SessionProbe;
import com.android.launcher3.ui.ClockController;
import com.android.launcher3.ui.MediaWidget;
import com.android.launcher3.ui.StatusController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Única pantalla: inicio (reloj, música, favoritos, barra) y el panel de todas las apps,
 * que es una vista superpuesta dentro de la misma Activity (sin segunda Activity ni animaciones).
 */
public final class HomeActivity extends Activity implements AppsRepository.Listener, JancarBridge.Listener, SessionProbe.Listener {

    private Prefs prefs;
    private AppsRepository apps;
    private JancarBridge bridge;
    private SessionProbe sessions;

    private View homeRoot;
    private View appsPanel;
    private GridView favGrid;
    private GridView appsGrid;
    private TextView appsCount;
    private ImageView btnToggleHidden;
    private ClockController clock;
    private StatusController status;
    private MediaWidget mediaWidget;
    private FavAdapter favAdapter;
    private AppsAdapter appsAdapter;

    private List<String> favKeys;
    private Set<String> hidden;
    private boolean showHidden;
    private int favIconPx;
    private int appIconPx;
    private boolean globalsWritten;

    // ------------------------------------------------------------------ ciclo de vida

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LauncherApp app = LauncherApp.get();
        prefs = app.prefs();
        apps = app.apps();
        bridge = app.bridge();
        sessions = app.sessions();
        favKeys = new ArrayList<>(prefs.getFavorites());
        hidden = prefs.getHidden();
        showHidden = prefs.getShowHidden();
        favIconPx = getResources().getDimensionPixelSize(R.dimen.fav_icon);
        appIconPx = getResources().getDimensionPixelSize(R.dimen.app_icon);

        setContentView(R.layout.activity_home);
        homeRoot = findViewById(R.id.home_root);
        appsPanel = findViewById(R.id.apps_panel);
        favGrid = findViewById(R.id.fav_grid);
        appsGrid = findViewById(R.id.apps_grid);
        appsCount = findViewById(R.id.apps_count);
        btnToggleHidden = findViewById(R.id.btn_toggle_hidden);

        clock = new ClockController(this, (TextView) findViewById(R.id.clock), (TextView) findViewById(R.id.date));
        status = new StatusController(this, (ImageView) findViewById(R.id.status_bt), (ImageView) findViewById(R.id.status_wifi),
                (TextView) findViewById(R.id.status_text));
        View mediaCard = findViewById(R.id.media_card);
        mediaWidget = new MediaWidget(mediaCard);

        favAdapter = new FavAdapter();
        favGrid.setAdapter(favAdapter);
        favGrid.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int h = b - t;
                if (h > 0 && h != (ob - ot)) favAdapter.setRowHeight((h - favGrid.getVerticalSpacing()) / 2);
            }
        });
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
        findViewById(R.id.media_play).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { bridge.mediaPlayPause(); } });
        findViewById(R.id.media_next).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { bridge.mediaNext(); } });
        findViewById(R.id.media_prev).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { bridge.mediaPrev(); } });

        findViewById(R.id.btn_all_apps).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showApps(); } });
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
        bridge.setHomeVisible(true);
        clock.start();
        status.start();
        bridge.requestMediaState();
        writeSystemUiGlobals();
    }

    @Override protected void onPause() {
        bridge.setHomeVisible(false);
        clock.stop();
        status.stop();
        super.onPause();
    }

    @Override protected void onStop() {
        apps.removeListener(this);
        bridge.removeListener(this);
        sessions.removeListener(this);
        super.onStop();
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
        favAdapter.rebuild();
        if (appsPanel.getVisibility() == View.VISIBLE) refreshApps();
    }

    // ------------------------------------------------------------------ JancarBridge.Listener

    @Override public void onMediaChanged(MediaState state) { renderMedia(); }

    @Override public void onServicesChanged() { renderMedia(); }

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
        mediaWidget.render(m, bridge.isMediaOnline());
    }

    @Override public void onAccChanged(boolean on) { /* el servicio apaga la pantalla; nada que hacer aquí */ }

    @Override public void onCcdChanged(int status) { /* la cámara la dibuja el sistema */ }

    // ------------------------------------------------------------------ acciones

    private void openMediaApp() {
        String pkg;
        MediaState cur = bridge.getMedia();
        if (cur.isThirdParty() && cur.sourcePackage != null && apps.launchPackage(this, cur.sourcePackage)) return;
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
        favAdapter.rebuild();
        Toast.makeText(this, e.label, Toast.LENGTH_SHORT).show();
    }

    private void removeFavorite(AppEntry e) {
        List<String> resolved = new ArrayList<>();
        for (String k : favKeys) { AppEntry r = apps.resolve(k); if (r != null && !r.key.equals(e.key)) resolved.add(r.key); }
        favKeys = resolved;
        prefs.setFavorites(favKeys);
        favAdapter.rebuild();
    }

    // ------------------------------------------------------------------ adaptadores

    private static final class Holder {
        ImageView icon; TextView label; AppEntry entry;
    }

    /** 8 huecos fijos (4×2). Un hueco vacío abre "todas las apps". */
    private final class FavAdapter extends BaseAdapter {
        private final AppEntry[] slots = new AppEntry[Prefs.MAX_FAVORITES];
        private int rowHeight;

        void rebuild() {
            int n = 0;
            for (String k : favKeys) {
                AppEntry e = apps.resolve(k);
                if (e != null && n < slots.length) slots[n++] = e;
            }
            for (int i = n; i < slots.length; i++) slots[i] = null;
            notifyDataSetChanged();
        }

        void setRowHeight(int h) {
            if (h > 0 && h != rowHeight) { rowHeight = h; notifyDataSetChanged(); }
        }

        @Override public int getCount() { return slots.length; }
        @Override public AppEntry getItem(int pos) { return slots[pos]; }
        @Override public long getItemId(int pos) { return pos; }
        @Override public boolean hasStableIds() { return true; }

        @Override public View getView(final int pos, View convert, ViewGroup parent) {
            Holder h;
            if (convert == null) {
                convert = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fav, parent, false);
                h = new Holder();
                h.icon = convert.findViewById(R.id.icon);
                h.label = convert.findViewById(R.id.label);
                convert.setTag(h);
            } else {
                h = (Holder) convert.getTag();
            }
            if (rowHeight > 0) {
                ViewGroup.LayoutParams lp = convert.getLayoutParams();
                if (lp == null) lp = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
                if (lp.height != rowHeight) { lp.height = rowHeight; convert.setLayoutParams(lp); }
            }
            final AppEntry e = slots[pos];
            h.entry = e;
            if (e == null) {
                convert.setBackgroundResource(R.drawable.tile_empty_bg);
                h.icon.setTag(null);
                h.icon.setImageResource(R.drawable.ic_add);
                h.icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.text_dim)));
                h.label.setText(R.string.empty_slot);
                h.label.setTextColor(getColor(R.color.text_dim));
                convert.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showApps(); } });
                convert.setOnLongClickListener(null);
            } else {
                convert.setBackgroundResource(R.drawable.tile_bg);
                h.icon.setImageTintList(null);
                h.label.setText(e.label);
                h.label.setTextColor(getColor(R.color.text));
                apps.bindIcon(e, h.icon, favIconPx);
                convert.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { apps.launch(HomeActivity.this, e); } });
                convert.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { showAppMenu(e, true); return true; }
                });
            }
            return convert;
        }
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
