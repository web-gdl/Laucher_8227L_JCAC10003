package com.android.launcher3;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.car.GpsTracker;
import com.android.launcher3.car.Trip;
import com.android.launcher3.car.TripService;
import com.android.launcher3.jancar.JancarBridge;
import com.android.launcher3.jancar.MediaState;
import com.android.launcher3.media.MediaMemory;
import com.android.launcher3.media.SessionProbe;
import com.android.launcher3.ui.CompassView;
import com.android.launcher3.ui.MediaWidget;
import com.android.launcher3.ui.SpeedGaugeView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Única pantalla: inicio «nocturno» (música + instrumentos del GPS + dock de favoritos) y el panel de todas las
 * apps, que es una vista superpuesta dentro de la misma Activity (sin segunda Activity ni animaciones).
 *
 * La hora y el estado (Wi-Fi, Bluetooth, GPS, USB) no se dibujan: ya los muestra la barra del sistema.
 */
public final class HomeActivity extends Activity implements AppsRepository.Listener, JancarBridge.Listener, SessionProbe.Listener, GpsTracker.Listener {

    private static final int REQ_LOCATION = 1;

    private Prefs prefs;
    private AppsRepository apps;
    private JancarBridge bridge;
    private SessionProbe sessions;
    private MediaMemory memory;
    private GpsTracker gps;

    private SpeedGaugeView speedGauge;
    private CompassView compass;
    private TextView headingText, gpsStatus, odoTrip, odoTotal;
    private String lastTrip, lastTotal, lastHeading;
    private boolean askedLocation;

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
    private final Runnable tick = () -> renderMedia();

    // ------------------------------------------------------------------ ciclo de vida

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LauncherApp app = LauncherApp.get();
        prefs = app.prefs();
        apps = app.apps();
        bridge = app.bridge();
        sessions = app.sessions();
        memory = app.memory();
        gps = app.gps();
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
        appsGrid.setOnItemClickListener((p, v, pos, id) -> {
            AppEntry e = appsAdapter.getItem(pos);
            if (e != null) { apps.launch(HomeActivity.this, e); }
        });
        appsGrid.setOnItemLongClickListener((p, v, pos, id) -> {
            AppEntry e = appsAdapter.getItem(pos);
            if (e != null) showAppMenu(e, false);
            return true;
        });

        speedGauge = findViewById(R.id.speed_gauge);
        compass = findViewById(R.id.compass);
        headingText = findViewById(R.id.heading);
        gpsStatus = findViewById(R.id.gps_status);
        odoTrip = findViewById(R.id.odo_trip);
        odoTotal = findViewById(R.id.odo_total);
        findViewById(R.id.gauges_card).setOnClickListener(v -> onGaugesTapped());
        // Velocímetro: límite de velocidad. Odómetro: reiniciar o ajustar (pulsación larga: atajos directos).
        speedGauge.setOnClickListener(v -> { if (!fixGps()) editSpeedLimit(); });
        findViewById(R.id.odo_trip_box).setOnClickListener(v -> showOdometerMenu());
        findViewById(R.id.odo_total_box).setOnClickListener(v -> showOdometerMenu());
        findViewById(R.id.odo_trip_box).setOnLongClickListener(v -> { confirmResetTrip(); return true; });
        findViewById(R.id.odo_total_box).setOnLongClickListener(v -> { editTotal(); return true; });

        mediaCard.setOnClickListener(v -> openMediaApp());
        mediaCard.setOnLongClickListener(v -> { chooseMediaApp(); return true; });
        findViewById(R.id.media_play).setOnClickListener(v -> {
            // Sin fuente activa pero con última canción: reabrir su app (ella sigue donde iba).
            if (!bridge.getMedia().hasContent() && memory.hasLast() && memory.openApp("toque play")) return;
            bridge.mediaPlayPause();
        });
        findViewById(R.id.media_next).setOnClickListener(v -> bridge.mediaNext());
        findViewById(R.id.media_prev).setOnClickListener(v -> bridge.mediaPrev());

        findViewById(R.id.btn_screen_off).setOnClickListener(v -> {
            if (!bridge.screenOff()) Toast.makeText(HomeActivity.this, R.string.media_service_offline, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());
        findViewById(R.id.btn_diag).setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, DiagnosticsActivity.class)));
        findViewById(R.id.btn_apps_back).setOnClickListener(v -> showHome());
        btnToggleHidden.setOnClickListener(v -> {
            showHidden = !showHidden;
            prefs.setShowHidden(showHidden);
            refreshApps();
        });
        Diag.log("home", "onCreate");
    }

    @Override protected void onStart() {
        super.onStart();
        apps.addListener(this);
        bridge.addListener(this);
        sessions.addListener(this);
        gps.addListener(this);
        onAppsChanged(apps.getApps());
        renderMedia();
        ensureGps();
        onGpsChanged(gps);
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
        gps.removeListener(this);
        // Fuera de la pantalla de inicio: deja en caché solo la mitad de los iconos (los del dock vuelven al instante).
        apps.trimIcons(false);
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

    // ------------------------------------------------------------------ instrumentos (GPS)

    /** Con permiso y el auto encendido, que el servicio esté midiendo; sin permiso, pedirlo una vez por arranque. */
    private void ensureGps() {
        if (gps.hasPermission()) {
            if (!gps.isRunning() && bridge.isAccOn()) TripService.start(this);
        } else if (!askedLocation) {
            askedLocation = true;
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQ_LOCATION) return;
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        Diag.log("gps", "permiso de ubicación " + (ok ? "concedido" : "denegado"));
        if (ok) TripService.start(this);
        onGpsChanged(gps);
    }

    private void onGaugesTapped() { fixGps(); }

    /** Si falta el permiso o el GPS está apagado, lleva a resolverlo y devuelve true. */
    private boolean fixGps() {
        switch (gps.state()) {
            case NO_PERMISSION:
                if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION) || !askedLocation) {
                    askedLocation = true;
                    requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
                } else {
                    // «No volver a preguntar»: solo queda la pantalla de la app en Ajustes
                    tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                }
                break;
            case DISABLED:
                tryStart(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                break;
            default:
                return false;
        }
        return true;
    }

    private void tryStart(Intent i) {
        try { startActivity(i); } catch (ActivityNotFoundException | SecurityException e) { Diag.warn("gps", "abrir ajustes", e); }
    }

    @Override public void onGpsChanged(GpsTracker g) {
        GpsTracker.State st = g.state();
        speedGauge.setSpeed(g.speedKmh());
        speedGauge.setLimit(LauncherApp.get().speedAlert().limitKmh());
        float b = g.bearing();
        compass.setHeading(b);
        String heading = b < 0 ? "—" : Trip.cardinal(b) + " " + Math.round(b) + "°";
        if (!heading.equals(lastHeading)) { lastHeading = heading; headingText.setText(heading); }
        String trip = getString(R.string.km_format, String.format(Locale.US, "%.1f", g.tripKm()));
        if (!trip.equals(lastTrip)) { lastTrip = trip; odoTrip.setText(trip); }
        String total = getString(R.string.km_format, formatThousands(Math.round(Math.floor(g.totalKm()))));
        if (!total.equals(lastTotal)) { lastTotal = total; odoTotal.setText(total); }
        int msg = st == GpsTracker.State.NO_PERMISSION ? R.string.gps_no_permission
                : st == GpsTracker.State.DISABLED ? R.string.gps_disabled
                : st == GpsTracker.State.SEARCHING && g.isRunning() ? R.string.gps_searching : 0;
        if (msg == 0) {
            if (gpsStatus.getVisibility() != View.GONE) gpsStatus.setVisibility(View.GONE);
        } else {
            gpsStatus.setText(msg);
            gpsStatus.setVisibility(View.VISIBLE);
        }
    }

    /** 48213 → «48 213» (espacio fino, igual en español e inglés). */
    private static String formatThousands(long v) {
        String s = Long.toString(v);
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append('\u202F');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private void confirmResetTrip() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.odo_reset_title)
                .setMessage(R.string.odo_reset_msg)
                .setPositiveButton(R.string.odo_reset, (d, w) -> gps.resetTrip())
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private void showOdometerMenu() {
        CharSequence[] items = {getString(R.string.odo_menu_reset_trip), getString(R.string.odo_menu_set_trip), getString(R.string.odo_menu_set_total)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.odo_menu_title)
                .setItems(items, (d, which) -> {
                    if (which == 0) confirmResetTrip();
                    else if (which == 1) editTrip();
                    else editTotal();
                })
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private void editTrip() {
        askNumber(R.string.odo_trip_title, R.string.odo_trip_msg, String.format(Locale.US, "%.1f", gps.tripKm()), true, R.string.odo_save,
                v -> { if (v >= 0 && v < 1_000_000) gps.setTripKm(v); }, 0, null);
    }

    private void editTotal() {
        askNumber(R.string.odo_total_title, R.string.odo_total_msg, String.valueOf((long) Math.floor(gps.totalKm())), false, R.string.odo_save,
                v -> { if (v >= 0 && v < 10_000_000) gps.setTotalKm(Math.floor(v)); }, 0, null);
    }

    private void editSpeedLimit() {
        final com.android.launcher3.car.SpeedAlert alert = LauncherApp.get().speedAlert();
        int cur = alert.limitKmh();
        askNumber(R.string.limit_title, R.string.limit_msg, String.valueOf(cur > 0 ? cur : 110), false, R.string.odo_save,
                v -> { if (v >= 10 && v <= 250) { alert.setLimitKmh((int) v); onGpsChanged(gps); } },
                R.string.limit_off, () -> { alert.setLimitKmh(0); onGpsChanged(gps); });
    }

    private interface NumberCallback { void onNumber(double value); }

    /** Diálogo con un campo numérico; botón neutral opcional (p. ej. «Desactivar»). */
    private void askNumber(int title, int message, String initial, boolean decimal, int okText, NumberCallback ok,
                           int neutralText, Runnable neutral) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        input.setTextSize(24);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(box)
                .setPositiveButton(okText, (d, w) -> {
                    try { ok.onNumber(Double.parseDouble(input.getText().toString().trim().replace(',', '.'))); }
                    catch (NumberFormatException ignored) {}
                })
                .setNegativeButton(R.string.cancel_btn, null);
        if (neutral != null) b.setNeutralButton(neutralText, (d, w) -> neutral.run());
        b.show();
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
        // Suelta las celdas del panel (vistas, listas de dibujo y texturas de sus iconos); se rehacen al abrirlo.
        appsAdapter.setItems(new ArrayList<AppEntry>());
    }

    private void refreshApps() {
        List<AppEntry> all = apps.getApps();
        List<AppEntry> shown = new ArrayList<>(all.size());
        for (AppEntry e : all) if (showHidden || !hidden.contains(e.key)) shown.add(e);
        appsAdapter.setItems(shown);
        appsCount.setText(hidden.isEmpty() ? String.valueOf(shown.size()) : getString(R.string.apps_count_hidden, shown.size(), hidden.size()));
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

    /** Música del inicio: estado del servicio completado con la sesión multimedia (fuentes de terceros). */
    private void renderMedia() {
        MediaState m = sessions.enrich(bridge.getMedia());
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
        all.setOnClickListener(v -> showApps());
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
                r.root.setOnClickListener(v -> showApps());
                r.root.setOnLongClickListener(null);
            } else {
                r.icon.setImageTintList(null);
                r.icon.setPadding(0, 0, 0, 0);
                r.icon.setAlpha(0.9f); // icono a color, apenas atenuado para la noche
                r.label.setText(e.label);
                apps.bindIcon(e, r.icon, ringIconPx);
                r.root.setOnClickListener(v -> apps.launch(HomeActivity.this, e));
                r.root.setOnLongClickListener(v -> { showAppMenu(e, true); return true; });
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
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    prefs.setMediaApp(list.get(which).packageName);
                    Diag.log("home", "app de música: " + list.get(which).packageName);
                    d.dismiss();
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
        actions.add(() -> { if (isFav) removeFavorite(e); else addFavorite(e); });
        if (!fromFavorites) {
            items.add(getString(isHidden ? R.string.show_hidden : R.string.hide_app));
            actions.add(() -> {
                if (isHidden) hidden.remove(e.key); else hidden.add(e.key);
                prefs.setHidden(hidden);
                refreshApps();
            });
        }
        items.add(getString(R.string.app_info));
        actions.add(() -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ex) { Toast.makeText(HomeActivity.this, e.packageName, Toast.LENGTH_SHORT).show(); }
        });
        items.add(getString(R.string.uninstall));
        actions.add(() -> {
            try {
                startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ex) { Toast.makeText(HomeActivity.this, e.packageName, Toast.LENGTH_SHORT).show(); }
        });
        new AlertDialog.Builder(this)
                .setTitle(e.label)
                .setItems(items.toArray(new String[0]), (d, which) -> actions.get(which).run())
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
