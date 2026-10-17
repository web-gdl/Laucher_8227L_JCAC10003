package com.android.launcher3.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.format.DateFormat;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Reloj y fecha. Se actualiza solo con ACTION_TIME_TICK (una vez por minuto, y solo mientras la
 * pantalla está en primer plano). Los formateadores se crean una vez y se reutilizan.
 */
public final class ClockController {
    private final Context ctx;
    private final TextView clock;
    private final TextView date;
    private final Date now = new Date();
    private SimpleDateFormat timeFmt;
    private SimpleDateFormat dateFmt;
    private boolean registered;

    public ClockController(Context ctx, TextView clock, TextView date) {
        this.ctx = ctx;
        this.clock = clock;
        this.date = date;
        rebuildFormats();
    }

    private void rebuildFormats() {
        Locale loc = Locale.getDefault();
        boolean h24 = DateFormat.is24HourFormat(ctx);
        timeFmt = new SimpleDateFormat(DateFormat.getBestDateTimePattern(loc, h24 ? "Hm" : "hm"), loc);
        dateFmt = new SimpleDateFormat(DateFormat.getBestDateTimePattern(loc, "EEEEdMMMM"), loc);
    }

    public void start() {
        if (!registered) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            f.addAction(Intent.ACTION_DATE_CHANGED);
            f.addAction(Intent.ACTION_LOCALE_CHANGED);
            ctx.registerReceiver(receiver, f);
            registered = true;
        }
        update();
    }

    public void stop() {
        if (registered) {
            try { ctx.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) {}
            registered = false;
        }
    }

    public void update() {
        now.setTime(System.currentTimeMillis());
        String t = timeFmt.format(now);
        String d = dateFmt.format(now);
        if (!d.isEmpty()) d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
        clock.setText(t);
        date.setText(d);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_TIMEZONE_CHANGED.equals(a) || Intent.ACTION_LOCALE_CHANGED.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a)) {
                rebuildFormats();
            }
            update();
        }
    };
}
