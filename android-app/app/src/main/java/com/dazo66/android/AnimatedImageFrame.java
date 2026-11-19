package com.dazo66.android;

import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.view.View;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.drawable.ScalingUtils;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ImageDecodeOptions;
import com.facebook.imagepipeline.image.ImageInfo;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.uimanager.ThemedReactContext;

/** GIF/WebP playback belongs to the visible page, never to speculative image loading. */
public final class AnimatedImageFrame extends SimpleDraweeView implements LifecycleEventListener {
    private final ThemedReactContext reactContext;
    private String source;
    private boolean active, listening, resumed;

    AnimatedImageFrame(ThemedReactContext context) {
        super(context);
        reactContext = context;
        resumed = context.getReactApplicationContext().getLifecycleState() == LifecycleState.RESUMED;
    }

    void setSource(String value) {
        if (java.util.Objects.equals(source, value)) return;
        source = value;
        if (value == null) { setController(null); return; }
        // Decode a still preview, but do not decode the entire animation up front.
        var request = ImageRequestBuilder.newBuilderWithSource(Uri.parse(value))
            .setImageDecodeOptions(ImageDecodeOptions.newBuilder()
                .setDecodePreviewFrame(true).setDecodeAllFrames(false).build()).build();
        setController(Fresco.newDraweeControllerBuilder()
            .setOldController(getController()).setImageRequest(request)
            .setAutoPlayAnimations(false)
            .setControllerListener(new BaseControllerListener<ImageInfo>() {
                @Override public void onFinalImageSet(String id, ImageInfo info, Animatable animation) {
                    updateAnimation(animation);
                }
            }).build());
    }

    void setActive(boolean value) { active = value; updateAnimation(); }
    void setContain(boolean value) {
        getHierarchy().setActualImageScaleType(value ? ScalingUtils.ScaleType.FIT_CENTER : ScalingUtils.ScaleType.CENTER_CROP);
    }
    private void updateAnimation() {
        updateAnimation(getController() == null ? null : getController().getAnimatable());
    }
    private void updateAnimation(Animatable animation) {
        if (animation == null) return;
        if (active && resumed && isAttachedToWindow() && isShown() && getWindowVisibility() == View.VISIBLE) {
            if (!animation.isRunning()) animation.start();
        } else animation.stop();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!listening) { reactContext.addLifecycleEventListener(this); listening = true; }
        resumed = reactContext.getReactApplicationContext().getLifecycleState() == LifecycleState.RESUMED;
        updateAnimation();
    }
    @Override protected void onDetachedFromWindow() {
        if (getController() != null && getController().getAnimatable() != null) getController().getAnimatable().stop();
        if (listening) { reactContext.removeLifecycleEventListener(this); listening = false; }
        super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimation();
    }
    @Override public void onHostResume() { resumed = true; updateAnimation(); }
    @Override public void onHostPause() { resumed = false; updateAnimation(); }
    @Override public void onHostDestroy() { resumed = false; updateAnimation(); }
    void release() {
        active = false;
        updateAnimation();
        if (listening) { reactContext.removeLifecycleEventListener(this); listening = false; }
        setController(null);
    }
}
