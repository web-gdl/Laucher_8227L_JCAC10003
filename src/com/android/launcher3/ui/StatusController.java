package com.android.launcher3.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.Diag;
import com.android.launcher3.R;

import java.util.HashSet;
import java.util.Set;

/**
 * Iconos de Bluetooth y Wi-Fi. Sin sondeo: solo broadcasts y NetworkCallback, y solo mientras
 * la pantalla está en primer plano.
 *
 * Nota: la pila BT de estos equipos es propia (com.jancar.btservice); el estado "conectado" se
 * deduce de los broadcasts ACL del framework, igual que hacía el launcher de fábrica.
 */
public final class StatusController {
    private final Context ctx;
    private final ImageView bt;
    private final ImageView wifi;
    private final TextView text;
    private final int colorOn;
    private final int colorDim;
    private final Set<String> aclDevices = new HashSet<>();
    private boolean btEnabled;
    private boolean btConnected;
    private boolean wifiUp;
    private int wifiLevel = -1;
    private boolean registered;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback netCb;

    public StatusController(Context ctx, ImageView bt, ImageView wifi, TextView text) {
        this.ctx = ctx;
        this.bt = bt;
        this.wifi = wifi;
        this.text = text;
        colorOn = ctx.getColor(R.color.accent);
        colorDim = ctx.getColor(R.color.text_dim);
    }

    public void start() {
        if (registered) return;
        registered = true;
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(WifiManager.RSSI_CHANGED_ACTION);
        ctx.registerReceiver(receiver, f);

        cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { post(true); }
                @Override public void onLost(Network n) { post(false); }
                private void post(final boolean up) {
                    wifi.post(new Runnable() { @Override public void run() { wifiUp = up; if (up) readRssi(); render(); } });
                }
            };
            try {
                cm.registerNetworkCallback(new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), netCb);
            } catch (RuntimeException e) { Diag.warn("status", "registerNetworkCallback", e); netCb = null; }
        }
        readInitialBt();
        readRssi();
        render();
    }

    public void stop() {
        if (!registered) return;
        registered = false;
        try { ctx.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) {}
        if (cm != null && netCb != null) { try { cm.unregisterNetworkCallback(netCb); } catch (RuntimeException ignored) {} }
        netCb = null;
    }

    private void readInitialBt() {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        btEnabled = a != null && a.isEnabled();
        boolean profile = false;
        if (btEnabled) {
            try {
                profile = a.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                        || a.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED;
            } catch (RuntimeException ignored) {}
        }
        btConnected = profile || !aclDevices.isEmpty();
    }

    private void readRssi() {
        wifiLevel = -1;
        if (!wifiUp) return;
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
            if (info != null) wifiLevel = WifiManager.calculateSignalLevel(info.getRssi(), 4);
        } catch (RuntimeException ignored) {}
    }

    private void render() {
        bt.setVisibility(btEnabled ? View.VISIBLE : View.GONE);
        bt.setImageTintList(android.content.res.ColorStateList.valueOf(btConnected ? colorOn : colorDim));
        wifi.setVisibility(wifiUp ? View.VISIBLE : View.GONE);
        if (wifiUp) {
            wifi.setImageTintList(android.content.res.ColorStateList.valueOf(colorOn));
            wifi.setAlpha(wifiLevel < 0 ? 1f : 0.4f + 0.2f * wifiLevel);
        }
        if (btConnected) text.setText(R.string.bt_connected);
        else text.setText("");
    }

    public String describe() {
        return "bt enabled=" + btEnabled + " connected=" + btConnected + " acl=" + aclDevices.size()
                + " | wifi up=" + wifiUp + " level=" + wifiLevel;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            switch (a) {
                case BluetoothAdapter.ACTION_STATE_CHANGED: {
                    int st = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                    btEnabled = st == BluetoothAdapter.STATE_ON;
                    if (!btEnabled) { aclDevices.clear(); btConnected = false; }
                    break;
                }
                case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED: {
                    int st = i.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED);
                    btConnected = st == BluetoothAdapter.STATE_CONNECTED || !aclDevices.isEmpty();
                    break;
                }
                case BluetoothDevice.ACTION_ACL_CONNECTED:
                case BluetoothDevice.ACTION_ACL_DISCONNECTED: {
                    BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String addr = d != null ? d.getAddress() : "?";
                    if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) aclDevices.add(addr); else aclDevices.remove(addr);
                    btConnected = !aclDevices.isEmpty();
                    Diag.log("status", a.substring(a.lastIndexOf('.') + 1) + " " + addr);
                    break;
                }
                case WifiManager.RSSI_CHANGED_ACTION:
                    readRssi();
                    break;
                default:
                    return;
            }
            render();
        }
    };
}
