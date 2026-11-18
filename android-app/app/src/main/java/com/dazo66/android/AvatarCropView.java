package com.dazo66.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

final class AvatarCropView extends View {
    private final Bitmap source;
    private final Paint paint = new Paint(3);
    private final ScaleGestureDetector detector;
    private float zoom = 1, offsetX = 0, offsetY = 0, lastX, lastY;
    AvatarCropView(Context context, Bitmap source) {
        super(context); this.source = source;
        detector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                zoom = Math.max(1, Math.min(4, zoom * d.getScaleFactor())); invalidate(); return true;
            }
        });
    }
    @Override protected void onDraw(Canvas canvas) { drawCrop(canvas, Math.min(getWidth(), getHeight())); }
    private void drawCrop(Canvas canvas, float size) {
        paint.setColor(0xfff1f5f9); canvas.drawColor(0xfff1f5f9);
        float scale = size / Math.min(source.getWidth(), source.getHeight()) * zoom;
        float width = source.getWidth() * scale, height = source.getHeight() * scale;
        float limitX = (width - size) / 2, limitY = (height - size) / 2;
        offsetX = Math.max(-limitX, Math.min(limitX, offsetX));
        offsetY = Math.max(-limitY, Math.min(limitY, offsetY));
        canvas.save(); canvas.clipRect(0, 0, size, size);
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(source, null, new RectF((size - width) / 2 + offsetX, (size - height) / 2 + offsetY,
                (size + width) / 2 + offsetX, (size + height) / 2 + offsetY), paint);
        canvas.restore();
    }
    Bitmap export() {
        int size = Math.min(getWidth(), getHeight());
        Bitmap output = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output); canvas.scale(256f / size, 256f / size); drawCrop(canvas, size);
        return output;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        detector.onTouchEvent(event);
        if (event.getPointerCount() == 1 && !detector.isInProgress()) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                offsetX += event.getX() - lastX; offsetY += event.getY() - lastY; invalidate();
            }
            lastX = event.getX(); lastY = event.getY();
        }
        return true;
    }
}
