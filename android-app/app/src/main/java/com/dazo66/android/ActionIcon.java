package com.dazo66.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class ActionIcon extends View {
    private final String kind;
    private final int color;
    private final Paint p = new Paint(3);
    ActionIcon(Context context, String kind) { this(context, kind, 0xff218ec5); }
    ActionIcon(Context context, String kind, int color) { super(context); this.kind = kind; this.color = color; }
    @Override protected void onDraw(Canvas canvas) {
        float unit = Math.min(getWidth(), getHeight()) / 48f;
        canvas.save(); canvas.translate((getWidth() - 48 * unit) / 2, (getHeight() - 48 * unit) / 2); canvas.scale(unit, unit);
        p.setColor(kind.equals("trash") ? 0xffc43d4b : color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.8f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        switch (kind) {
            case "back":
                line(canvas, 32, 24, 15, 24); line(canvas, 15, 24, 23, 16); line(canvas, 15, 24, 23, 32); break;
            case "sort":
                line(canvas, 14, 15, 34, 15); line(canvas, 14, 23, 29, 23); line(canvas, 14, 31, 24, 31);
                line(canvas, 34, 24, 34, 34); line(canvas, 30, 30, 34, 34); line(canvas, 34, 34, 38, 30); break;
            case "upload":
                line(canvas, 24, 30, 24, 12); line(canvas, 17, 19, 24, 12); line(canvas, 24, 12, 31, 19);
                tray(canvas); break;
            case "download":
                line(canvas, 24, 12, 24, 30); line(canvas, 17, 23, 24, 30); line(canvas, 24, 30, 31, 23);
                tray(canvas); break;
            case "previous":
                line(canvas, 30, 15, 20, 24); line(canvas, 20, 24, 30, 33); break;
            case "next":
                line(canvas, 18, 15, 28, 24); line(canvas, 28, 24, 18, 33); break;
            case "folder":
                Path folder = new Path(); folder.moveTo(10, 17); folder.lineTo(20, 17); folder.lineTo(23, 20);
                folder.lineTo(38, 20); folder.lineTo(38, 35); folder.lineTo(10, 35); folder.close(); canvas.drawPath(folder, p); break;
            case "plus":
                line(canvas, 24, 13, 24, 35); line(canvas, 13, 24, 35, 24); break;
            case "edit":
                line(canvas, 14, 34, 19, 33); line(canvas, 19, 33, 34, 18); line(canvas, 34, 18, 30, 14);
                line(canvas, 30, 14, 15, 29); line(canvas, 15, 29, 14, 34); break;
            case "trash":
                line(canvas, 16, 17, 32, 17); line(canvas, 20, 14, 28, 14); line(canvas, 18, 20, 19, 34);
                line(canvas, 19, 34, 29, 34); line(canvas, 29, 34, 30, 20); break;
            case "external":
                line(canvas, 24, 15, 34, 15); line(canvas, 34, 15, 34, 25); line(canvas, 34, 15, 21, 28);
                line(canvas, 30, 28, 30, 34); line(canvas, 30, 34, 14, 34); line(canvas, 14, 34, 14, 18); line(canvas, 14, 18, 20, 18); break;
            case "reset":
                cArc(canvas); line(canvas, 14, 15, 14, 24); line(canvas, 14, 24, 23, 24); break;
            case "home":
                line(canvas, 11, 23, 24, 12); line(canvas, 24, 12, 37, 23); line(canvas, 16, 21, 16, 36);
                line(canvas, 16, 36, 32, 36); line(canvas, 32, 36, 32, 21); break;
            case "artist":
                canvas.drawCircle(24, 17, 6, p); canvas.drawArc(12, 23, 36, 39, 180, 180, false, p); break;
            case "gallery":
                canvas.drawRoundRect(10, 13, 38, 35, 3, 3, p); canvas.drawCircle(18, 20, 2, p);
                line(canvas, 13, 32, 21, 25); line(canvas, 21, 25, 26, 29); line(canvas, 26, 29, 32, 23); line(canvas, 32, 23, 36, 27); break;
            case "more":
                p.setStyle(Paint.Style.FILL); canvas.drawCircle(14, 24, 2.6f, p); canvas.drawCircle(24, 24, 2.6f, p); canvas.drawCircle(34, 24, 2.6f, p); break;
            case "search":
                canvas.drawCircle(21, 21, 9, p); line(canvas, 28, 28, 36, 36); break;
            case "close":
                line(canvas, 17, 17, 31, 31); line(canvas, 31, 17, 17, 31); break;
            case "filter":
                line(canvas, 12, 16, 36, 16); line(canvas, 12, 24, 36, 24); line(canvas, 12, 32, 36, 32);
                canvas.drawCircle(20, 16, 3, p); canvas.drawCircle(29, 24, 3, p); canvas.drawCircle(19, 32, 3, p); break;
            case "history":
                canvas.drawCircle(25, 25, 12, p); line(canvas, 25, 18, 25, 26); line(canvas, 25, 26, 31, 29); break;
            case "task":
                canvas.drawRoundRect(12, 14, 36, 36, 3, 3, p); line(canvas, 18, 11, 18, 18); line(canvas, 30, 11, 30, 18);
                line(canvas, 12, 22, 36, 22); line(canvas, 20, 29, 23, 32); line(canvas, 23, 32, 29, 26); break;
            case "play":
                Path triangle = new Path(); triangle.moveTo(19, 14); triangle.lineTo(34, 24); triangle.lineTo(19, 34); triangle.close(); canvas.drawPath(triangle, p); break;
            case "lock":
                canvas.drawRoundRect(14, 22, 34, 37, 3, 3, p); canvas.drawArc(18, 11, 30, 30, 180, 180, false, p); break;
            case "storage":
                canvas.drawRoundRect(11, 14, 37, 35, 4, 4, p); line(canvas, 11, 28, 37, 28); canvas.drawCircle(30, 32, 1, p); break;
            case "settings":
                canvas.drawCircle(24, 24, 10, p); canvas.drawCircle(24, 24, 4, p);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    line(canvas, 24+(float)Math.cos(angle)*10, 24+(float)Math.sin(angle)*10,
                            24+(float)Math.cos(angle)*14, 24+(float)Math.sin(angle)*14);
                }
                break;
            case "shield":
                Path shield = new Path(); shield.moveTo(24, 11); shield.lineTo(36, 16); shield.lineTo(34, 30); shield.lineTo(24, 38); shield.lineTo(14, 30); shield.lineTo(12, 16); shield.close(); canvas.drawPath(shield, p); break;
        }
        canvas.restore();
    }
    private void line(Canvas c, float x1, float y1, float x2, float y2) { c.drawLine(x1, y1, x2, y2, p); }
    private void tray(Canvas c) { line(c, 13, 32, 13, 37); line(c, 13, 37, 35, 37); line(c, 35, 37, 35, 32); }
    private void cArc(Canvas c) { c.drawArc(14, 14, 35, 35, -130, 280, false, p); }
}
