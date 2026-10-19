package com.android.launcher3.jancar;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

import com.android.launcher3.Diag;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cliente binder propio (sin SDK del fabricante) para los servicios de com.jancar.services.
 *
 * Se conecta a tres servicios:
 *   - media  (IMedia):  título/artista/portada/estado + play-pausa/siguiente/anterior.
 *   - system (ISystem): openMemoryApp al arrancar y closeBackLight (apagar pantalla).
 *   - car    (ICar):    estado ACC y cámara de reversa.
 *
 * Reconexión: linkToDeath + onServiceDisconnected + broadcast "com.jancar.services.ready",
 * con reintentos espaciados (1, 2, 5, 10, 30 s). Todas las notificaciones a la UI van por el hilo principal.
 *
 * Códigos de transacción tomados de los stubs AIDL del APK de fábrica (ivi-launcher 2.0, servicios 3.0.0).
 */
public final class JancarBridge {

    public interface Listener {
        void onMediaChanged(MediaState state);
        void onServicesChanged();
        void onAccChanged(boolean on);
        void onCcdChanged(int status);
    }

    public static final String SERVICES_PACKAGE = "com.jancar.services";
    private static final String ACTION_MAIN = "com.jancar.services.action.main";
    private static final String ACTION_MEDIA = "com.jancar.services.action.media";
    private static final String ACTION_SYSTEM = "com.jancar.services.action.system";
    private static final String ACTION_CAR = "com.jancar.services.action.car";
    private static final String ACTION_READY = "com.jancar.services.ready";
    private static final String ACTION_ACC = "com.jancar.services.action.acc";

    private static final String DESC_MEDIA = "com.jancar.services.media.IMedia";
    private static final String DESC_MEDIA_CB = "com.jancar.services.media.IMediaInfoCallback";
    private static final String DESC_SYSTEM = "com.jancar.services.system.ISystem";
    private static final String DESC_CAR = "com.jancar.services.car.ICar";
    private static final String DESC_CAR_CB = "com.jancar.services.car.ICarCallback";

    // IMedia
    private static final int MEDIA_REGISTER_INFO_CB = 5;
    private static final int MEDIA_UNREGISTER_INFO_CB = 6;
    private static final int MEDIA_GET_ACTIVE = 7;
    private static final int MEDIA_PLAY_PAUSE = 17;
    private static final int MEDIA_PREV = 18;
    private static final int MEDIA_NEXT = 19;
    private static final int MEDIA_REQUEST_INFO_AND_STATE = 22;
    // IMediaInfoCallback
    private static final int CB_ON_MEDIA_CHANGE = 1;
    private static final int CB_ON_PLAY_STATE_CHANGE = 2;
    private static final int CB_ON_MEDIA_ZONE_CHANGED = 3;
    private static final int CB_ON_SHOWN_TYPE_CHANGED = 4;
    // ISystem
    private static final int SYSTEM_CLOSE_BACKLIGHT = 10;
    private static final int SYSTEM_IS_OPEN_BACKLIGHT = 13;
    private static final int SYSTEM_OPEN_MEMORY_APP = 18;
    private static final int SYSTEM_GET_MEMORY_APP = 19;
    // ICar
    private static final int CAR_REGISTER_CB = 3;
    private static final int CAR_UNREGISTER_CB = 4;
    private static final int CAR_GET_CCD_STATUS = 10;
    private static final int CAR_IS_ACC_ON = 52;
    // ICarCallback
    private static final int CARCB_ON_ACC_CHANGED = 2;
    private static final int CARCB_ON_CCD_CHANGED = 3;
    private static final int CARCB_MAX_CODE = 32;

    private static final long[] RETRY_MS = {1000, 2000, 5000, 10000, 30000};
    private static final int MAX_COVER_SIDE = 1024;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final MediaState media = new MediaState();
    private final MediaInfoCallback mediaCb = new MediaInfoCallback();
    private final CarCallback carCb = new CarCallback();

    final Link mediaLink = new Link("media", ACTION_MEDIA);
    final Link systemLink = new Link("system", ACTION_SYSTEM);
    final Link carLink = new Link("car", ACTION_CAR);

    private boolean accOn = true;
    private boolean accKnown;
    private int ccdStatus;
    private boolean homeVisible;
    private boolean readyRegistered;
    /** Último valor de ACC visto en el broadcast abierto (contraste con isAccOn del servicio); null si no ha llegado. */
    private Boolean accBroadcast;
    private int lastCoverHash;
    private int lastCoverW, lastCoverH;
    private String lastEvent = "-";
    private String lastInfoSig = "";

    public JancarBridge(Context context) {
        app = context.getApplicationContext();
    }

    // ------------------------------------------------------------------ ciclo de vida

    public void start() {
        if (!readyRegistered) {
            readyRegistered = true;
            app.registerReceiver(readyReceiver, new IntentFilter(ACTION_READY));
            app.registerReceiver(accReceiver, new IntentFilter(ACTION_ACC));
        }
        connectAll();
    }

    public void connectAll() {
        mediaLink.connect();
        systemLink.connect();
        carLink.connect();
    }

    /** Reintento manual (pantalla de diagnóstico). */
    public void reconnect() {
        Diag.log("bridge", "reconexión manual");
        mediaLink.reset();
        systemLink.reset();
        carLink.reset();
        connectAll();
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    public void setHomeVisible(boolean visible) { homeVisible = visible; }

    public boolean isHomeVisible() { return homeVisible; }

    public MediaState getMedia() { return media; }

    public boolean isMediaOnline() { return mediaLink.binder != null; }

    public boolean isSystemOnline() { return systemLink.binder != null; }

    public boolean isCarOnline() { return carLink.binder != null; }

    public boolean isAccOn() { return accOn; }

    public boolean isAccKnown() { return accKnown; }

    public int getCcdStatus() { return ccdStatus; }

    public String getLastEvent() { return lastEvent; }

    public Boolean getAccBroadcast() { return accBroadcast; }

    public String describeLinks() {
        return mediaLink.describe() + "\n" + systemLink.describe() + "\n" + carLink.describe();
    }

    private final BroadcastReceiver readyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Diag.log("bridge", "servicio listo (broadcast)");
            connectAll();
        }
    };

    private final BroadcastReceiver accReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!i.hasExtra("acc")) return;
            accBroadcast = i.getBooleanExtra("acc", true);
            Diag.log("car", "broadcast acc=" + accBroadcast + " (servicio dice " + (accKnown ? accOn : "?") + ")");
            // El broadcast llega aunque el callback del servicio no: también se avisa (los oyentes toleran duplicados).
            for (Listener l : new ArrayList<>(listeners)) l.onAccChanged(accBroadcast);
        }
    };

    // ------------------------------------------------------------------ enlace a un servicio

    final class Link implements ServiceConnection, IBinder.DeathRecipient {
        final String name;
        final String action;
        volatile IBinder binder;
        boolean bound;
        int attempt;
        int connections;
        long lastConnectedAt;
        private final Runnable retry = new Runnable() { @Override public void run() { connect(); } };

        Link(String name, String action) { this.name = name; this.action = action; }

        void connect() {
            if (binder != null) return;
            main.removeCallbacks(retry);
            Intent i = new Intent(action).setPackage(SERVICES_PACKAGE);
            // El SDK de fábrica arranca el servicio principal antes de enlazar; en segundo plano puede estar prohibido.
            try { app.startService(new Intent(ACTION_MAIN).setPackage(SERVICES_PACKAGE)); } catch (RuntimeException ignored) {}
            boolean ok;
            try {
                if (bound) { try { app.unbindService(this); } catch (RuntimeException ignored) {} bound = false; }
                ok = app.bindService(i, this, Context.BIND_AUTO_CREATE);
                bound = ok;
            } catch (RuntimeException e) {
                Diag.warn("bridge", name + " bindService", e);
                ok = false;
            }
            if (!ok) scheduleRetry();
        }

        void scheduleRetry() {
            long delay = RETRY_MS[Math.min(attempt, RETRY_MS.length - 1)];
            attempt++;
            Diag.log("bridge", name + " reintento en " + delay + " ms");
            main.removeCallbacks(retry);
            main.postDelayed(retry, delay);
        }

        void reset() {
            main.removeCallbacks(retry);
            IBinder b = binder;
            binder = null;
            if (b != null) { try { b.unlinkToDeath(this, 0); } catch (RuntimeException ignored) {} }
            if (bound) { try { app.unbindService(this); } catch (RuntimeException ignored) {} bound = false; }
            attempt = 0;
        }

        @Override public void onServiceConnected(ComponentName cn, IBinder b) {
            if (b == null) { scheduleRetry(); return; }
            try { b.linkToDeath(this, 0); } catch (RemoteException e) { scheduleRetry(); return; }
            binder = b;
            attempt = 0;
            connections++;
            lastConnectedAt = System.currentTimeMillis();
            Diag.log("bridge", name + " conectado");
            onLinked(this);
            notifyServices();
        }

        @Override public void onServiceDisconnected(ComponentName cn) {
            Diag.log("bridge", name + " desconectado");
            dropAndRetry();
        }

        @Override public void binderDied() {
            Diag.log("bridge", name + " murió");
            main.post(new Runnable() { @Override public void run() { dropAndRetry(); } });
        }

        private void dropAndRetry() {
            IBinder b = binder;
            binder = null;
            if (b != null) { try { b.unlinkToDeath(this, 0); } catch (RuntimeException ignored) {} }
            onUnlinked(this);
            notifyServices();
            scheduleRetry();
        }

        String describe() {
            return name + ": " + (binder != null ? "conectado" : "sin conexión") + " (conexiones=" + connections
                    + ", intento=" + attempt + (lastConnectedAt > 0 ? ", última=" + ((System.currentTimeMillis() - lastConnectedAt) / 1000) + " s" : "") + ")";
        }
    }

    private void onLinked(Link link) {
        if (link == mediaLink) {
            transactVoid(link.binder, DESC_MEDIA, MEDIA_REGISTER_INFO_CB, mediaCb);
            requestMediaState();
        } else if (link == carLink) {
            transactVoid(link.binder, DESC_CAR, CAR_REGISTER_CB, carCb);
            Boolean acc = transactBool(link.binder, DESC_CAR, CAR_IS_ACC_ON);
            if (acc != null) {
                accOn = acc; accKnown = true;
                // Estado inicial: el proceso suele nacer con el auto apagado (el servicio lo cierra al apagar).
                for (Listener l : new ArrayList<>(listeners)) l.onAccChanged(acc);
            }
            Integer ccd = transactInt(link.binder, DESC_CAR, CAR_GET_CCD_STATUS);
            if (ccd != null) ccdStatus = ccd;
            Diag.log("bridge", "acc=" + accOn + " ccd=" + ccdStatus);
        }
    }

    private void onUnlinked(Link link) {
        if (link == mediaLink) {
            // El servicio se cayó: lo que mostrábamos ya no es fiable.
            media.type = MediaState.TYPE_NONE;
            media.state = MediaState.STATE_STOPPED;
            media.title = "";
            media.artist = "";
            media.position = 0;
            media.duration = 0;
            replaceCover(null);
            notifyMedia();
        }
    }

    // ------------------------------------------------------------------ órdenes

    public void requestMediaState() {
        IBinder b = mediaLink.binder;
        if (b != null) transactVoid(b, DESC_MEDIA, MEDIA_REQUEST_INFO_AND_STATE, null);
    }

    public void mediaPlayPause() { simpleMedia(MEDIA_PLAY_PAUSE, "play/pausa"); }

    public void mediaNext() { simpleMedia(MEDIA_NEXT, "siguiente"); }

    public void mediaPrev() { simpleMedia(MEDIA_PREV, "anterior"); }

    private void simpleMedia(int code, String what) {
        IBinder b = mediaLink.binder;
        Diag.log("media", what + (b != null ? "" : " (sin servicio)"));
        if (b != null) transactVoid(b, DESC_MEDIA, code, null);
    }

    public int mediaGetActiveType() {
        IBinder b = mediaLink.binder;
        Integer v = b != null ? transactInt(b, DESC_MEDIA, MEDIA_GET_ACTIVE) : null;
        return v != null ? v : MediaState.TYPE_NONE;
    }

    /** Apaga la retroiluminación como lo hace el launcher de fábrica (closeBackLight(1)). */
    public boolean screenOff() {
        IBinder b = systemLink.binder;
        if (b == null) return false;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESC_SYSTEM);
            data.writeInt(1);
            b.transact(SYSTEM_CLOSE_BACKLIGHT, data, reply, 0);
            reply.readException();
            Diag.log("system", "closeBackLight(1)");
            return true;
        } catch (RemoteException | RuntimeException e) {
            Diag.warn("system", "closeBackLight", e);
            return false;
        } finally { data.recycle(); reply.recycle(); }
    }

    /** Pide al servicio que reabra la última app multimedia ("app de memoria"), como el launcher de fábrica 6 s tras arrancar. */
    public boolean openMemoryApp() {
        IBinder b = systemLink.binder;
        if (b == null) { Diag.log("system", "openMemoryApp: sin servicio"); return false; }
        String pkg = transactString(b, DESC_SYSTEM, SYSTEM_GET_MEMORY_APP);
        Diag.log("system", "openMemoryApp (memoria=" + pkg + ")");
        return transactVoid(b, DESC_SYSTEM, SYSTEM_OPEN_MEMORY_APP, null);
    }

    public String getMemoryAppPackage() {
        IBinder b = systemLink.binder;
        return b != null ? transactString(b, DESC_SYSTEM, SYSTEM_GET_MEMORY_APP) : null;
    }

    public Boolean isBacklightOn() {
        IBinder b = systemLink.binder;
        return b != null ? transactBool(b, DESC_SYSTEM, SYSTEM_IS_OPEN_BACKLIGHT) : null;
    }

    // ------------------------------------------------------------------ helpers binder

    private static boolean transactVoid(IBinder b, String desc, int code, IBinder strong) {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(desc);
            if (strong != null) data.writeStrongBinder(strong);
            b.transact(code, data, reply, 0);
            reply.readException();
            return true;
        } catch (RemoteException | RuntimeException e) {
            Diag.warn("bridge", desc + "#" + code, e);
            return false;
        } finally { data.recycle(); reply.recycle(); }
    }

    private static Integer transactInt(IBinder b, String desc, int code) {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(desc);
            b.transact(code, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (RemoteException | RuntimeException e) {
            Diag.warn("bridge", desc + "#" + code, e);
            return null;
        } finally { data.recycle(); reply.recycle(); }
    }

    private static Boolean transactBool(IBinder b, String desc, int code) {
        Integer v = transactInt(b, desc, code);
        return v == null ? null : v != 0;
    }

    private static String transactString(IBinder b, String desc, int code) {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(desc);
            b.transact(code, data, reply, 0);
            reply.readException();
            return reply.readString();
        } catch (RemoteException | RuntimeException e) {
            Diag.warn("bridge", desc + "#" + code, e);
            return null;
        } finally { data.recycle(); reply.recycle(); }
    }

    // ------------------------------------------------------------------ callbacks (hilo binder → principal)

    /** Stub de com.jancar.services.media.IMediaInfoCallback. */
    private final class MediaInfoCallback extends Binder implements IInterface {
        MediaInfoCallback() { attachInterface(this, DESC_MEDIA_CB); }

        @Override public IBinder asBinder() { return this; }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case CB_ON_MEDIA_CHANGE: {
                    data.enforceInterface(DESC_MEDIA_CB);
                    final int type = data.readInt();
                    final String title = data.readString();
                    final String artist = data.readString();
                    final int w = data.readInt();
                    final int h = data.readInt();
                    final byte[] pixels = data.createByteArray();
                    final int index = data.readInt();
                    final int total = data.readInt();
                    final boolean fav = data.readInt() != 0;
                    final Bitmap bmp = decodeCover(pixels, w, h);
                    main.post(new Runnable() {
                        @Override public void run() { applyMediaInfo(type, title, artist, bmp, index, total, pixels == null, fav); }
                    });
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                case CB_ON_PLAY_STATE_CHANGE: {
                    data.enforceInterface(DESC_MEDIA_CB);
                    final int type = data.readInt();
                    final int state = data.readInt();
                    final int position = data.readInt();
                    final int duration = data.readInt();
                    main.post(new Runnable() {
                        @Override public void run() { applyPlayState(type, state, position, duration); }
                    });
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                case CB_ON_MEDIA_ZONE_CHANGED: {
                    data.enforceInterface(DESC_MEDIA_CB);
                    data.readInt(); data.readInt();
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                case CB_ON_SHOWN_TYPE_CHANGED: {
                    data.enforceInterface(DESC_MEDIA_CB);
                    final int type = data.readInt();
                    main.post(new Runnable() { @Override public void run() { lastEvent = "shownType=" + type; } });
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    /** Stub de com.jancar.services.car.ICarCallback: solo ACC y cámara de reversa; el resto se acepta y se ignora. */
    private final class CarCallback extends Binder implements IInterface {
        CarCallback() { attachInterface(this, DESC_CAR_CB); }

        @Override public IBinder asBinder() { return this; }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code < 1 || code > CARCB_MAX_CODE) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(DESC_CAR_CB);
            if (code == CARCB_ON_ACC_CHANGED) {
                final boolean on = data.readInt() != 0;
                main.post(new Runnable() {
                    @Override public void run() {
                        accOn = on; accKnown = true; lastEvent = "acc=" + on;
                        Diag.log("car", "ACC " + (on ? "ON" : "OFF"));
                        for (Listener l : new ArrayList<>(listeners)) l.onAccChanged(on);
                    }
                });
            } else if (code == CARCB_ON_CCD_CHANGED) {
                final int status = data.readInt();
                main.post(new Runnable() {
                    @Override public void run() {
                        ccdStatus = status; lastEvent = "ccd=" + status;
                        Diag.log("car", "CCD " + status);
                        for (Listener l : new ArrayList<>(listeners)) l.onCcdChanged(status);
                    }
                });
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    }

    // ------------------------------------------------------------------ estado multimedia

    private Bitmap decodeCover(byte[] pixels, int w, int h) {
        if (pixels == null || w <= 0 || h <= 0 || w > MAX_COVER_SIDE || h > MAX_COVER_SIDE) return null;
        int hash = Arrays.hashCode(pixels);
        synchronized (this) {
            if (hash == lastCoverHash && w == lastCoverW && h == lastCoverH && media.cover != null && !media.cover.isRecycled()) {
                return media.cover; // misma portada: no se vuelve a crear
            }
        }
        try {
            Bitmap bmp;
            long argb = (long) w * h * 4, rgb565 = (long) w * h * 2;
            if (pixels.length >= argb) {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(ByteBuffer.wrap(pixels, 0, (int) argb));
            } else if (pixels.length >= rgb565) {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
                bmp.copyPixelsFromBuffer(ByteBuffer.wrap(pixels, 0, (int) rgb565));
            } else {
                return null;
            }
            synchronized (this) { lastCoverHash = hash; lastCoverW = w; lastCoverH = h; }
            return bmp;
        } catch (RuntimeException | OutOfMemoryError e) {
            Diag.warn("media", "portada " + w + "x" + h + " (" + pixels.length + " B)", e);
            return null;
        }
    }

    private void applyMediaInfo(int type, String title, String artist, Bitmap cover, int index, int total, boolean noPixels, boolean fav) {
        media.type = type;
        media.title = title != null ? title : "";
        media.artist = artist != null ? artist : "";
        media.index = index;
        media.total = total;
        media.lastInfoAt = System.currentTimeMillis();
        if (cover != media.cover) replaceCover(cover);
        lastEvent = "mediaChange type=" + type;
        // El servicio repite la misma información cada pocos segundos: solo se registra si cambia algo.
        String sig = type + "|" + media.title + "|" + media.artist + "|" + index + "/" + total;
        if (!sig.equals(lastInfoSig)) { lastInfoSig = sig; Diag.log("media", "info " + media); }
        notifyMedia();
    }

    private void applyPlayState(int type, int state, int position, int duration) {
        boolean changed = media.type != type || media.state != state;
        media.type = type;
        media.state = state;
        media.position = position;
        media.duration = duration;
        media.lastStateAt = System.currentTimeMillis();
        if (changed) { lastEvent = "playState type=" + type + " state=" + state; Diag.log("media", "estado " + state + " tipo " + type); }
        notifyMedia();
    }

    /** Sustituye la portada y recicla la anterior después de que la UI haya cambiado de bitmap. */
    private void replaceCover(Bitmap fresh) {
        final Bitmap old;
        synchronized (this) {
            old = media.cover;
            media.cover = fresh;
            if (fresh == null) { lastCoverHash = 0; lastCoverW = 0; lastCoverH = 0; }
        }
        if (old != null && old != fresh) {
            main.post(new Runnable() {
                @Override public void run() { if (!old.isRecycled()) old.recycle(); }
            });
        }
    }

    private void notifyMedia() {
        for (Listener l : new ArrayList<>(listeners)) l.onMediaChanged(media);
    }

    private void notifyServices() {
        for (Listener l : new ArrayList<>(listeners)) l.onServicesChanged();
    }
}
