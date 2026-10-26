package com.rqmbooks.rafiqalquran;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Decorative opening artwork: deep emerald night, gold eight-fold geometry,
 * slow stars and a faint minaret silhouette. All drawing is local; no images
 * or network are required.
 */
public final class IntroArt extends View {
    private static final int DEEP = Color.rgb(3, 12, 8);
    private static final int EMERALD = Color.rgb(8, 54, 38);
    private static final int GOLD = Color.rgb(212, 175, 90);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random(17);
    private final float[] stars = new float[160];
    private long started = SystemClock.uptimeMillis();
    private boolean running;

    public IntroArt(Context context) {
        super(context);
        init();
    }

    public IntroArt(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.1f);
        line.setColor(GOLD);
        for (int i = 0; i < stars.length; i += 4) {
            stars[i] = random.nextFloat();
            stars[i + 1] = random.nextFloat() * 0.94f;
            stars[i + 2] = 0.5f + random.nextFloat() * 2.2f;
            stars[i + 3] = 0.25f + random.nextFloat() * 0.75f;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        started = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0 || h <= 0) return;
        final float time = (SystemClock.uptimeMillis() - started) / 1000f;

        paint.setShader(new LinearGradient(0, 0, w, h, EMERALD, DEEP, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(new RadialGradient(w * 0.5f, h * 0.34f, w * 1.05f,
                Color.argb(96, 34, 160, 113), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // Eight-fold geometric lattice, very low contrast.
        final float cell = w / 4.6f;
        line.setColor(Color.argb(22, 212, 175, 90));
        for (int row = -1; row < (int) (h / cell) + 2; row++) {
            for (int col = -1; col < (int) (w / cell) + 2; col++) {
                final float cx = col * cell + (row % 2 == 0 ? 0 : cell / 2f);
                final float cy = row * cell * 0.86f;
                drawStar(canvas, cx, cy, cell * 0.24f, line, time * 0.015f + row * 0.04f);
            }
        }

        // Twinkling stars.
        for (int i = 0; i < stars.length; i += 4) {
            float twinkle = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * stars[i + 2] + i));
            paint.setColor(Color.argb((int) (110 * stars[i + 3] * twinkle), 240, 226, 186));
            canvas.drawCircle(stars[i] * w, stars[i + 1] * h, 1.1f + stars[i + 3], paint);
        }

        drawMinaret(canvas, w, h);

        paint.setShader(new LinearGradient(0, h * 0.72f, 0, h,
                Color.TRANSPARENT, Color.argb(230, 3, 9, 6), Shader.TileMode.CLAMP));
        canvas.drawRect(0, h * 0.72f, w, h, paint);
        paint.setShader(null);

        if (running) postInvalidateOnAnimation();
    }

    private void drawStar(Canvas canvas, float cx, float cy, float radius, Paint p, float rotation) {
        path.reset();
        for (int i = 0; i < 16; i++) {
            double angle = rotation + i * Math.PI / 8.0 - Math.PI / 2.0;
            float r = (i % 2 == 0) ? radius : radius * 0.45f;
            float x = cx + (float) Math.cos(angle) * r;
            float y = cy + (float) Math.sin(angle) * r;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        canvas.drawPath(path, p);
    }

    private void drawMinaret(Canvas canvas, float w, float h) {
        final float cx = w * 0.5f;
        final float base = h * 0.93f;
        final float top = h * 0.56f;
        path.reset();
        path.moveTo(cx - w * 0.06f, base);
        path.lineTo(cx - w * 0.035f, top + h * 0.07f);
        path.lineTo(cx - w * 0.028f, top + h * 0.03f);
        path.lineTo(cx - w * 0.022f, top);
        path.cubicTo(cx - w * 0.02f, top - h * 0.025f, cx + w * 0.02f, top - h * 0.025f, cx + w * 0.022f, top);
        path.lineTo(cx + w * 0.028f, top + h * 0.03f);
        path.lineTo(cx + w * 0.035f, top + h * 0.07f);
        path.lineTo(cx + w * 0.06f, base);
        path.close();
        paint.setColor(Color.argb(120, 2, 14, 9));
        canvas.drawPath(path, paint);
        line.setColor(Color.argb(38, 212, 175, 90));
        line.setStrokeWidth(1.2f);
        canvas.drawPath(path, line);
        paint.setColor(Color.argb(80, 3, 20, 13));
        canvas.drawRect(cx - w * 0.10f, base - h * 0.02f, cx + w * 0.10f, base, paint);
    }
}
