package com.dazo66.android;

import android.content.Context;
import android.net.Uri;
import android.widget.FrameLayout;
import android.widget.VideoView;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import java.util.*;

public final class DazoPackage implements ReactPackage {
    private ApiClient api;
    private synchronized ApiClient client(Context context) { if (api == null) api = new ApiClient(context); return api; }
    @Override public List<NativeModule> createNativeModules(ReactApplicationContext context) { return Collections.singletonList(new DazoBridge(context, client(context))); }
    @Override public List<ViewManager> createViewManagers(ReactApplicationContext context) { return Arrays.asList(new IconManager(), new VideoManager(client(context))); }

    public static final class IconFrame extends FrameLayout {
        String kind = "home"; int color = 0xff218ec5;
        IconFrame(Context context) { super(context); }
        void update() {
            removeAllViews(); ActionIcon icon = new ActionIcon(getContext(), kind, color); addView(icon, new LayoutParams(-1,-1));
            post(() -> { icon.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY)); icon.layout(0,0,getWidth(),getHeight()); });
        }
    }
    public static final class IconManager extends SimpleViewManager<IconFrame> {
        @Override public String getName() { return "DazoIcon"; }
        @Override protected IconFrame createViewInstance(ThemedReactContext context) { return new IconFrame(context); }
        @ReactProp(name="kind") public void setKind(IconFrame view, String kind) { view.kind = kind; view.update(); }
        @ReactProp(name="color", customType="Color", defaultInt=0xff218ec5) public void setColor(IconFrame view, int color) { view.color = color; view.update(); }
    }
    public static final class VideoFrame extends FrameLayout {
        final VideoView video;
        android.media.MediaPlayer player; float rate = 1;
        List<String> addresses = Collections.emptyList(); int addressIndex, seekPosition;
        boolean dropped;
        VideoFrame(Context context) { super(context); video = new VideoView(context); addView(video, new LayoutParams(-1,-1,android.view.Gravity.CENTER)); }
        void layoutVideo() {
            video.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            int width = video.getMeasuredWidth(), height = video.getMeasuredHeight();
            int left = (getWidth()-width)/2, top = (getHeight()-height)/2;
            video.layout(left, top, left+width, top+height);
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) { layoutVideo(); }
        @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if (video != null && visibility != VISIBLE) video.pause(); }
    }
    public static final class VideoManager extends SimpleViewManager<VideoFrame> {
        private final ApiClient api;
        VideoManager(ApiClient api) { this.api = api; }
        @Override public String getName() { return "DazoVideo"; }
        @Override protected VideoFrame createViewInstance(ThemedReactContext context) { return new VideoFrame(context); }
        @ReactProp(name="path") public void setPath(VideoFrame view, String path) {
            if (path == null) return;
            view.addresses = api.mediaAddresses(path); view.addressIndex = 0; view.seekPosition = 0; view.dropped = false;
            play(view);
        }
        private void play(VideoFrame view) {
            if (view.dropped || view.addresses.isEmpty()) return;
            view.video.stopPlayback(); view.player = null;
            String address = view.addresses.get(view.addressIndex);
            view.video.setVideoURI(Uri.parse(address), api.trusted(address) ? Collections.singletonMap("Cookie", api.cookie()) : Collections.emptyMap());
            view.video.setOnErrorListener((player, what, extra) -> {
                if (view.addressIndex + 1 >= view.addresses.size()) return false;
                try { view.seekPosition = view.video.getCurrentPosition(); } catch (Exception ignored) {}
                view.addressIndex++; view.post(() -> play(view)); return true;
            });
            view.video.setMediaController(new android.widget.MediaController(view.getContext())); view.video.setOnPreparedListener(mp -> {
                view.player = mp;
                try { mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(view.rate)); } catch (Exception ignored) {}
                view.layoutVideo(); if (view.seekPosition > 0) view.video.seekTo(view.seekPosition); view.video.start();
            });
        }
        @ReactProp(name="rate", defaultFloat=1) public void setRate(VideoFrame view, float rate) { view.rate = rate; if (view.player != null) try { view.player.setPlaybackParams(view.player.getPlaybackParams().setSpeed(rate)); } catch (Exception ignored) {} }
        @Override public void onDropViewInstance(VideoFrame view) { view.dropped = true; view.video.stopPlayback(); super.onDropViewInstance(view); }
    }
}
