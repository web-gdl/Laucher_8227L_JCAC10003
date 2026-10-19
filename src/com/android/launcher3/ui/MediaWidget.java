package com.android.launcher3.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.launcher3.Diag;
import com.android.launcher3.R;
import com.android.launcher3.jancar.MediaState;

/**
 * Pinta el estado multimedia del inicio nocturno: portada redonda, halo de color detrás,
 * título, artista, progreso y tiempos. Sin temporizadores propios: solo redibuja cuando le avisan.
 *
 * El halo es la portada reducida a {@link #HALO_PX}x{@link #HALO_PX} píxeles y ampliada con
 * filtrado bilineal: parece un desenfoque fuerte pero cuesta un único escalado por portada
 * (unos cientos de bytes), nada por fotograma.
 */
public final class MediaWidget {
    private static final int HALO_PX = 12;

    private final Context ctx;
    private final ImageView cover;
    private final ImageView halo;
    private final TextView source;
    private final TextView title;
    private final TextView artist;
    private final ProgressBar progress;
    private final View times;
    private final TextView pos;
    private final TextView dur;
    private final ImageButton play;
    private final int dimColor;
    private final int coverPad;
    private final StringBuilder sb = new StringBuilder(16);
    private Bitmap shownCover;
    private Bitmap haloBitmap;
    private boolean lastPlaying;
    private boolean lastOnline = true;

    public MediaWidget(View root, ImageView halo) {
        ctx = root.getContext();
        this.halo = halo;
        cover = root.findViewById(R.id.media_cover);
        source = root.findViewById(R.id.media_source);
        title = root.findViewById(R.id.media_title);
        artist = root.findViewById(R.id.media_artist);
        progress = root.findViewById(R.id.media_progress);
        times = root.findViewById(R.id.media_times);
        pos = root.findViewById(R.id.media_pos);
        dur = root.findViewById(R.id.media_dur);
        play = root.findViewById(R.id.media_play);
        dimColor = ctx.getColor(R.color.text_dim);
        coverPad = ctx.getResources().getDimensionPixelSize(R.dimen.cover_size) / 3;
        cover.setClipToOutline(true); // recorta la portada al óvalo del fondo
        title.setSelected(true);       // marquee
        setCover(null);
    }

    public void render(MediaState s, boolean online) {
        if (!online) {
            if (lastOnline) {
                source.setText(R.string.media_service_offline);
                title.setText(R.string.media_idle);
                artist.setText("");
                setCover(null);
                showProgress(false);
                setPlaying(false);
                lastOnline = false;
            }
            return;
        }
        lastOnline = true;

        if (s.hasContent()) {
            source.setText(ctx.getString(R.string.media_now_playing) + " · " + ctx.getString(sourceLabel(s.type)));
            title.setText(s.title);
            artist.setText(s.artist == null ? "" : s.artist);
        } else {
            source.setText(sourceLabel(s.type));
            title.setText(R.string.media_idle);
            artist.setText(R.string.media_open_hint);
        }
        setCover(s.cover);
        setPlaying(s.isPlaying());

        if (s.hasContent() && s.duration > 0 && s.type != MediaState.TYPE_RADIO) {
            showProgress(true);
            int p = Math.max(0, Math.min(s.position, s.duration));
            progress.setProgress((int) (1000L * p / s.duration));
            sb.setLength(0); pos.setText(appendTime(sb, p));
            sb.setLength(0); dur.setText(appendTime(sb, s.duration));
        } else {
            showProgress(false);
        }
    }

    /** Libera el halo (bitmap propio). La portada es del puente y la recicla él. */
    public void release() {
        halo.setImageDrawable(null);
        if (haloBitmap != null && !haloBitmap.isRecycled()) haloBitmap.recycle();
        haloBitmap = null;
    }

    private void showProgress(boolean show) {
        int v = show ? View.VISIBLE : View.INVISIBLE;
        if (progress.getVisibility() != v) { progress.setVisibility(v); times.setVisibility(v); }
    }

    private void setCover(Bitmap b) {
        if (b == shownCover && b != null) return;
        shownCover = b;
        Bitmap oldHalo = haloBitmap;
        haloBitmap = null;
        if (b == null || b.isRecycled()) {
            cover.setImageResource(R.drawable.ic_music_note);
            cover.setImageTintList(ColorStateList.valueOf(dimColor));
            cover.setPadding(coverPad, coverPad, coverPad, coverPad);
            halo.setImageDrawable(null);
        } else {
            cover.setImageTintList(null);
            cover.setPadding(0, 0, 0, 0);
            cover.setImageBitmap(b);
            try {
                haloBitmap = Bitmap.createScaledBitmap(b, HALO_PX, HALO_PX, true);
                BitmapDrawable d = new BitmapDrawable(ctx.getResources(), haloBitmap);
                d.setFilterBitmap(true);
                halo.setImageDrawable(d);
            } catch (RuntimeException | OutOfMemoryError e) {
                Diag.warn("media", "halo", e);
                halo.setImageDrawable(null);
            }
        }
        if (oldHalo != null && oldHalo != haloBitmap && !oldHalo.isRecycled()) oldHalo.recycle();
    }

    private void setPlaying(boolean playing) {
        if (playing == lastPlaying && play.getDrawable() != null) return;
        lastPlaying = playing;
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(ctx.getString(playing ? R.string.pause : R.string.play));
    }

    private static int sourceLabel(int type) {
        switch (type) {
            case MediaState.TYPE_MUSIC: return R.string.media_source_music;
            case MediaState.TYPE_RADIO: return R.string.media_source_radio;
            case MediaState.TYPE_VIDEO: return R.string.media_source_video;
            case MediaState.TYPE_A2DP: return R.string.media_source_bt;
            case MediaState.TYPE_NONE: return R.string.media_source_music;
            default: return R.string.media_source_other;
        }
    }

    private static StringBuilder appendTime(StringBuilder sb, int seconds) {
        if (seconds < 0) seconds = 0;
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) sb.append(h).append(':');
        if (h > 0 && m < 10) sb.append('0');
        sb.append(m).append(':');
        if (s < 10) sb.append('0');
        sb.append(s);
        return sb;
    }
}
