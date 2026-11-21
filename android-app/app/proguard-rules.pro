# React Native discovers the app's legacy bridge and view properties by annotation.
# The React Native/Fresco/Media3 dependencies supply their own JNI and reflection rules.
-keepattributes RuntimeVisibleAnnotations,Signature,InnerClasses,EnclosingMethod
-keepclassmembers,allowoptimization class com.dazo66.android.DazoBridge {
    @com.facebook.react.bridge.ReactMethod <methods>;
}
-keepclassmembers,allowoptimization class com.dazo66.android.DazoPackage$* {
    @com.facebook.react.uimanager.annotations.ReactProp <methods>;
}

# Fresco discovers this optional factory and its constructor marker interfaces
# through Class.forName. Its consumer rules do not protect both marker names.
-keep class com.facebook.fresco.animation.factory.AnimatedFactoryV2Impl { *; }
-keep interface com.facebook.fresco.animation.bitmap.preparation.ondemandanimation.FrameLoaderListener { *; }
-keep interface com.facebook.fresco.vito.core.AnimatedImagePerfLoggingListener { *; }

# ImagePipelineFactory also constructs the optional decoders through reflection.
-keep class com.facebook.animated.gif.GifImageDecoder { public <init>(...); }
-keep class com.facebook.animated.webp.WebPImageDecoder { public <init>(...); }
