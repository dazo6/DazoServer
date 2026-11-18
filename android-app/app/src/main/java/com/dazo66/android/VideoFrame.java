package com.dazo66.android;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;
import java.util.Collections;
import java.util.List;

/** Black-backed streaming player with a two-second startup target and momentary hold-to-speed-up. */
@UnstableApi
final class VideoFrame extends FrameLayout {
    private final ApiClient api;
    private final PlayerView surface;
    private final ProgressBar spinner;
    private final TextView status, boostBadge;
    private final GestureDetector gestures;
    private ExoPlayer player;
    private List<String> addresses = Collections.emptyList();
    private int addressIndex;
    private long position;
    private float rate = 1;
    private boolean boosting, controlsTouch, released;

    VideoFrame(Context context, ApiClient api) {
        super(context); this.api = api; setBackgroundColor(Color.BLACK);
        surface = new PlayerView(context); surface.setBackgroundColor(Color.BLACK);
        surface.setShutterBackgroundColor(Color.BLACK); surface.setKeepContentOnPlayerReset(false);
        surface.setUseArtwork(false); surface.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        surface.setShowPreviousButton(false); surface.setShowNextButton(false);
        addView(surface, new LayoutParams(-1, -1));
        spinner = new ProgressBar(context); addView(spinner, new LayoutParams(dp(38), dp(38), Gravity.CENTER));
        status = label(context); LayoutParams text = new LayoutParams(-2, -2, Gravity.CENTER); text.topMargin = dp(82); addView(status, text);
        boostBadge = label(context); boostBadge.setBackgroundColor(0xcc17384e); boostBadge.setPadding(dp(16), dp(8), dp(16), dp(8));
        LayoutParams badge = new LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL); badge.topMargin = dp(16); addView(boostBadge, badge); boostBadge.setVisibility(GONE);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public void onLongPress(MotionEvent event) {
                if (controlsTouch || player == null || player.getPlaybackState() != Player.STATE_READY) return;
                boosting = true; applyRate(); boostBadge.setText(String.format(java.util.Locale.ROOT, "%s× 快进", rate * 2)); boostBadge.setVisibility(VISIBLE);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        });
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(Context context) { TextView view = new TextView(context); view.setTextColor(Color.WHITE); view.setTextSize(13); view.setGravity(Gravity.CENTER); return view; }
    void setPath(String path) { released = false; addresses = api.mediaAddresses(path); addressIndex = 0; position = 0; play(); }
    void setRate(float value) { rate = Math.max(0.25f, value); applyRate(); }
    private void applyRate() { if (player != null) player.setPlaybackSpeed(boosting ? rate * 2 : rate); }
    private void buffering(String text) { spinner.setVisibility(VISIBLE); status.setText(text); status.setVisibility(VISIBLE); }
    private void play() {
        if (released || addresses.isEmpty()) return;
        if (player != null) { surface.setPlayer(null); player.release(); }
        buffering("正在缓冲…"); surface.setVisibility(VISIBLE);
        String address = addresses.get(addressIndex);
        player = new ExoPlayer.Builder(getContext()).setLoadControl(new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 30000, 2000, 2000).setPrioritizeTimeOverSizeThresholds(true).build()).build();
        player.setMediaSource(new ProgressiveMediaSource.Factory(() -> new AuthenticatedMediaSource(api)).createMediaSource(MediaItem.fromUri(address)));
        surface.setPlayer(player); applyRate();
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) buffering("正在缓冲…");
                else if (state == Player.STATE_READY || state == Player.STATE_ENDED) { spinner.setVisibility(GONE); status.setVisibility(GONE); }
            }
            @Override public void onRenderedFirstFrame() { spinner.setVisibility(GONE); status.setVisibility(GONE); post(() -> layoutPlayer()); }
            @Override public void onPlayerError(PlaybackException error) {
                if (player != null) position = Math.max(0, player.getCurrentPosition());
                if (++addressIndex < addresses.size()) { post(() -> play()); return; }
                spinner.setVisibility(GONE); status.setText("视频加载失败，点击重试"); status.setVisibility(VISIBLE);
                status.setOnClickListener(view -> { addressIndex = 0; play(); });
            }
        });
        player.prepare(); if (position > 0) player.seekTo(position); player.play();
    }
    private boolean controlAt(View view, float x, float y) {
        Rect bounds = new Rect();
        if (view.getVisibility() != VISIBLE || !view.getGlobalVisibleRect(bounds) || !bounds.contains((int)x, (int)y)) return false;
        if (view instanceof android.widget.ImageButton || view.getClass().getName().contains("TimeBar")) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) if (controlAt(((ViewGroup)view).getChildAt(i), x, y)) return true;
        return false;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) controlsTouch = controlAt(surface, event.getRawX(), event.getRawY());
        gestures.onTouchEvent(event);
        boolean held = boosting;
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            boosting = false; boostBadge.setVisibility(GONE); applyRate();
        }
        return held || super.dispatchTouchEvent(event);
    }
    void release() { released = true; boosting = false; surface.setPlayer(null); if (player != null) player.release(); player = null; }
    private void layoutPlayer() { surface.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY)); surface.layout(0, 0, getWidth(), getHeight()); }
    // React Native owns this root's layout; native controls and transient overlays still need their own layout pass.
    private boolean layoutQueued;
    @Override public void requestLayout() {
        super.requestLayout();
        if (layoutQueued) return;
        layoutQueued = true;
        post(() -> {
            layoutQueued = false;
            if (getWidth() == 0 || getHeight() == 0) return;
            measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        });
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) { super.onLayout(changed, left, top, right, bottom); layoutPlayer(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if (player != null && visibility != VISIBLE) player.pause(); }
}
