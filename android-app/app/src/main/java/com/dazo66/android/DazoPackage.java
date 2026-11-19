package com.dazo66.android;

import android.content.Context;
import android.widget.FrameLayout;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import java.util.*;

public final class DazoPackage implements ReactPackage {
    private ApiClient client(Context context) { return ApiClient.get(context); }
    @Override public List<NativeModule> createNativeModules(ReactApplicationContext context) { return Collections.singletonList(new DazoBridge(context, client(context))); }
    @Override public List<ViewManager> createViewManagers(ReactApplicationContext context) { return Arrays.asList(new IconManager(), new VideoManager(client(context)), new AnimatedImageManager()); }
    public static final class AnimatedImageManager extends SimpleViewManager<AnimatedImageFrame> {
        @Override public String getName() { return "DazoAnimatedImage"; }
        @Override protected AnimatedImageFrame createViewInstance(ThemedReactContext context) { return new AnimatedImageFrame(context); }
        @ReactProp(name="source") public void setSource(AnimatedImageFrame view, String source) { view.setSource(source); }
        @ReactProp(name="active", defaultBoolean=false) public void setActive(AnimatedImageFrame view, boolean active) { view.setActive(active); }
        @ReactProp(name="contain", defaultBoolean=false) public void setContain(AnimatedImageFrame view, boolean contain) { view.setContain(contain); }
        @Override public void onDropViewInstance(AnimatedImageFrame view) { view.release(); super.onDropViewInstance(view); }
    }
    public static final class IconFrame extends FrameLayout {
        String kind = "home"; int color = 0xff218ec5; boolean shadow;
        IconFrame(Context context) { super(context); }
        void update() {
            removeAllViews(); ActionIcon icon = new ActionIcon(getContext(), kind, color, shadow); addView(icon, new LayoutParams(-1,-1));
            post(() -> { icon.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY)); icon.layout(0,0,getWidth(),getHeight()); });
        }
    }
    public static final class IconManager extends SimpleViewManager<IconFrame> {
        @Override public String getName() { return "DazoIcon"; }
        @Override protected IconFrame createViewInstance(ThemedReactContext context) { return new IconFrame(context); }
        @ReactProp(name="kind") public void setKind(IconFrame view, String kind) { view.kind = kind; view.update(); }
        @ReactProp(name="color", customType="Color", defaultInt=0xff218ec5) public void setColor(IconFrame view, int color) { view.color = color; view.update(); }
        @ReactProp(name="shadow", defaultBoolean=false) public void setShadow(IconFrame view, boolean shadow) { view.shadow = shadow; view.update(); }
    }
    @androidx.media3.common.util.UnstableApi
    public static final class VideoManager extends SimpleViewManager<VideoFrame> {
        private final ApiClient api;
        VideoManager(ApiClient api) { this.api = api; }
        @Override public String getName() { return "DazoVideo"; }
        @Override protected VideoFrame createViewInstance(ThemedReactContext context) { return new VideoFrame(context, api); }
        @ReactProp(name="path") public void setPath(VideoFrame view, String path) { if (path != null) view.setPath(path); }
        @ReactProp(name="rate", defaultFloat=1) public void setRate(VideoFrame view, float rate) { view.setRate(rate); }
        @Override public void onDropViewInstance(VideoFrame view) { view.release(); super.onDropViewInstance(view); }
    }
}
