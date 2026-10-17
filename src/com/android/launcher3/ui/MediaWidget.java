package com.android.launcher3.ui;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.launcher3.R;
import com.android.launcher3.jancar.MediaState;

/** Pinta el estado multimedia en la tarjeta. Sin temporizadores: solo redibuja cuando el servicio avisa. */
public final class MediaWidget {
    private final View card;
    private final ImageView cover;
    private final TextView source;
    private final TextView title;
    private final TextView artist;
    private final ProgressBar progress;
    private final TextView time;
    private final ImageButton play;
    private final int dimColor;
    private final StringBuilder sb = new StringBuilder(16);
    private Bitmap shownCover;
    private boolean lastPlaying;
    private int lastType = Integer.MIN_VALUE;
    private boolean lastOnline = true;

    public MediaWidget(View card) {
        this.card = card;
        cover = card.findViewById(R.id.media_cover);
        source = card.findViewById(R.id.media_source);
        title = card.findViewById(R.id.media_title);
        artist = card.findViewById(R.id.media_artist);
        progress = card.findViewById(R.id.media_progress);
        time = card.findViewById(R.id.media_time);
        play = card.findViewById(R.id.media_play);
        dimColor = card.getContext().getColor(R.color.text_dim);
        title.setSelected(true); // marquee
    }

    public void render(MediaState s, boolean online) {
        if (!online) {
            if (lastOnline) {
                source.setText(R.string.media_service_offline);
                title.setText(R.string.media_idle);
                artist.setText("");
                setCover(null);
                progress.setProgress(0);
                time.setText("");
                setPlaying(false);
                lastOnline = false;
            }
            return;
        }
        lastOnline = true;

        if (s.type != lastType) {
            lastType = s.type;
            source.setText(sourceLabel(s.type));
        }
        if (s.hasContent()) {
            title.setText(s.title);
            artist.setText(s.artist == null || s.artist.isEmpty() ? "" : s.artist);
        } else {
            title.setText(R.string.media_idle);
            artist.setText(R.string.media_open_hint);
        }
        setCover(s.cover);
        setPlaying(s.isPlaying());

        if (s.duration > 0 && s.type != MediaState.TYPE_RADIO) {
            progress.setVisibility(View.VISIBLE);
            time.setVisibility(View.VISIBLE);
            progress.setProgress((int) (1000L * Math.max(0, Math.min(s.position, s.duration)) / s.duration));
            sb.setLength(0);
            appendTime(sb, s.position).append(" / ");
            appendTime(sb, s.duration);
            time.setText(sb);
        } else {
            progress.setVisibility(View.INVISIBLE);
            time.setVisibility(View.INVISIBLE);
        }
    }

    private void setCover(Bitmap b) {
        if (b == shownCover && b != null) return;
        shownCover = b;
        if (b == null || b.isRecycled()) {
            cover.setImageResource(R.drawable.ic_music_note);
            cover.setImageTintList(ColorStateList.valueOf(dimColor));
            cover.setPadding(28, 28, 28, 28);
        } else {
            cover.setImageTintList(null);
            cover.setPadding(0, 0, 0, 0);
            cover.setImageBitmap(b);
        }
    }

    private void setPlaying(boolean playing) {
        if (playing == lastPlaying && play.getDrawable() != null) return;
        lastPlaying = playing;
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(card.getContext().getString(playing ? R.string.pause : R.string.play));
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
