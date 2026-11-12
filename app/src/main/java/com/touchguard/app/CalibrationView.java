package com.touchguard.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tela cheia da calibracao. Primeiro da 5 s para a pessoa soltar o celular; depois,
 * durante o tempo escolhido, todo toque que aparecer e registrado como fantasma
 * (posicao, tamanho do contato e duracao). Segurar o botao por 2 s cancela.
 */
final class CalibrationView extends View {

    interface Done {
        /** samples == null quando a calibracao foi cancelada. */
        void onDone(List<Prefs.Sample> samples);
    }

    private static final long PREP_MS = 5000;
    private static final long HOLD_MS = 2000;

    private static final class Pt {
        final float x, y, size;
        final long t;

        Pt(float x, float y, float size, long t) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.t = t;
        }
    }

    private final long recordMs;
    private final Done done;
    private final float d;
    private final long startAt = SystemClock.uptimeMillis();
    private final List<Prefs.Sample> samples = new ArrayList<>();
    private final List<float[]> dots = new ArrayList<>();
    private final SparseArray<Pt> active = new SparseArray<>();
    private final RectF btn = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean finished;
    private int holdId = -1;
    private long holdStart;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (finished) return;
            if (SystemClock.uptimeMillis() >= startAt + PREP_MS + recordMs) {
                finish(false);
                return;
            }
            invalidate();
            postDelayed(this, 200);
        }
    };

    private final Runnable cancelRun = () -> finish(true);

    CalibrationView(Context c, long recordMs, Done done) {
        super(c);
        this.recordMs = recordMs;
        this.done = done;
        this.d = c.getResources().getDisplayMetrics().density;
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        removeCallbacks(cancelRun);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float bw = 260 * d, bh = 56 * d;
        btn.set((w - bw) / 2f, h - 120 * d - bh, (w + bw) / 2f, h - 120 * d);
    }

    private boolean recording() {
        return SystemClock.uptimeMillis() >= startAt + PREP_MS;
    }

    private void finish(boolean cancelled) {
        if (finished) return;
        finished = true;
        removeCallbacks(tick);
        removeCallbacks(cancelRun);
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < active.size(); i++) addSample(active.valueAt(i), now);
        active.clear();
        done.onDone(cancelled ? null : samples);
    }

    private void addSample(Pt p, long now) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float[] n = Geo.toNatural(p.x / w, p.y / h, Geo.rotation(getContext()));
        samples.add(new Prefs.Sample(n[0], n[1], p.size, now - p.t));
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (finished) return true;
        int action = ev.getActionMasked();
        int idx = ev.getActionIndex();
        int id = ev.getPointerId(idx);
        long now = SystemClock.uptimeMillis();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = ev.getX(idx), y = ev.getY(idx);
                if (holdId < 0 && btn.contains(x, y)) {
                    holdId = id;
                    holdStart = now;
                    postDelayed(cancelRun, HOLD_MS);
                }
                if (recording()) {
                    active.put(id, new Pt(x, y, ev.getTouchMajor(idx) / d, now));
                    dots.add(new float[]{x, y});
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (id == holdId) {
                    removeCallbacks(cancelRun);
                    holdId = -1;
                }
                Pt p = active.get(id);
                if (p != null) {
                    active.remove(id);
                    addSample(p, now);
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(cancelRun);
                holdId = -1;
                for (int i = 0; i < active.size(); i++) addSample(active.valueAt(i), now);
                active.clear();
                break;
            }
            default:
                break;
        }
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        cv.drawColor(0xE6000000);
        long now = SystemClock.uptimeMillis();
        float cx = w / 2f;
        float cy = h * 0.36f;

        if (!recording()) {
            long left = (startAt + PREP_MS - now + 999) / 1000;
            textPaint.setTextSize(26 * d);
            cv.drawText("Solte o celular", cx, cy, textPaint);
            textPaint.setTextSize(72 * d);
            cv.drawText(String.valueOf(Math.max(1, left)), cx, cy + 96 * d, textPaint);
            textPaint.setTextSize(15 * d);
            textPaint.setColor(0xCCFFFFFF);
            cv.drawText("Deixe o aparelho parado, sem encostar na tela.", cx, cy + 150 * d, textPaint);
            cv.drawText("Todo toque registrado depois disso é fantasma.", cx, cy + 174 * d, textPaint);
            textPaint.setColor(0xFFFFFFFF);
        } else {
            long remain = Math.max(0, startAt + PREP_MS + recordMs - now);
            long s = (remain + 999) / 1000;
            textPaint.setTextSize(15 * d);
            textPaint.setColor(0xCCFFFFFF);
            cv.drawText("Registrando toques fantasmas — não toque na tela", cx, cy - 40 * d, textPaint);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(64 * d);
            cv.drawText(String.format(Locale.US, "%d:%02d", s / 60, s % 60), cx, cy + 30 * d, textPaint);
            textPaint.setTextSize(18 * d);
            cv.drawText("Toques detectados: " + (samples.size() + active.size()), cx, cy + 74 * d, textPaint);
        }

        // Pontos onde os toques fantasmas apareceram.
        for (float[] p : dots) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x66FF3B30);
            cv.drawCircle(p[0], p[1], 18 * d, paint);
            paint.setColor(0xFFFF3B30);
            cv.drawCircle(p[0], p[1], 5 * d, paint);
        }

        // Botao "segure para cancelar", com barra de progresso enquanto e segurado.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33FFFFFF);
        cv.drawRoundRect(btn, 28 * d, 28 * d, paint);
        if (holdId >= 0) {
            float f = Math.min(1f, (now - holdStart) / (float) HOLD_MS);
            paint.setColor(0x88FFFFFF);
            cv.save();
            cv.clipRect(btn.left, btn.top, btn.left + btn.width() * f, btn.bottom);
            cv.drawRoundRect(btn, 28 * d, 28 * d, paint);
            cv.restore();
            postInvalidateOnAnimation();
        }
        textPaint.setTextSize(15 * d);
        cv.drawText("Segure 2 s para cancelar", btn.centerX(), btn.centerY() + 5 * d, textPaint);
    }
}
