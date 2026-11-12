package com.touchguard.app;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometria da tela. As zonas sao salvas em coordenadas "naturais" (celular em pe),
 * normalizadas de 0 a 1, e convertidas para pixels da orientacao atual. Assim a zona
 * continua sobre o mesmo ponto fisico do vidro quando a tela gira.
 */
final class Geo {
    private Geo() {}

    static int rotation(Context c) {
        DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return Surface.ROTATION_0;
        Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
        return d == null ? Surface.ROTATION_0 : d.getRotation();
    }

    @SuppressWarnings("deprecation")
    static Point screenSize(WindowManager wm) {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect b = wm.getMaximumWindowMetrics().getBounds();
            return new Point(b.width(), b.height());
        }
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        return new Point(m.widthPixels, m.heightPixels);
    }

    /** Natural normalizado -> tela atual normalizada. */
    static float[] toScreen(float nx, float ny, int rot) {
        switch (rot) {
            case Surface.ROTATION_90:
                return new float[]{ny, 1f - nx};
            case Surface.ROTATION_180:
                return new float[]{1f - nx, 1f - ny};
            case Surface.ROTATION_270:
                return new float[]{1f - ny, nx};
            default:
                return new float[]{nx, ny};
        }
    }

    /** Tela atual normalizada -> natural normalizado (inverso de toScreen). */
    static float[] toNatural(float sx, float sy, int rot) {
        switch (rot) {
            case Surface.ROTATION_90:
                return new float[]{1f - sy, sx};
            case Surface.ROTATION_180:
                return new float[]{1f - sx, 1f - sy};
            case Surface.ROTATION_270:
                return new float[]{sy, 1f - sx};
            default:
                return new float[]{sx, sy};
        }
    }

    static Rect naturalToScreen(RectF n, int rot, int w, int h) {
        float[] a = toScreen(n.left, n.top, rot);
        float[] b = toScreen(n.right, n.bottom, rot);
        int l = Math.round(Math.min(a[0], b[0]) * w);
        int t = Math.round(Math.min(a[1], b[1]) * h);
        int r = Math.round(Math.max(a[0], b[0]) * w);
        int bo = Math.round(Math.max(a[1], b[1]) * h);
        return new Rect(l, t, r, bo);
    }

    /** a - b: devolve ate 4 retangulos que cobrem a sem cobrir b. */
    static List<Rect> subtract(Rect a, Rect b) {
        List<Rect> out = new ArrayList<>();
        if (!Rect.intersects(a, b)) {
            out.add(new Rect(a));
            return out;
        }
        if (b.top > a.top) out.add(new Rect(a.left, a.top, a.right, b.top));
        if (b.bottom < a.bottom) out.add(new Rect(a.left, b.bottom, a.right, a.bottom));
        int t = Math.max(a.top, b.top);
        int bo = Math.min(a.bottom, b.bottom);
        if (b.left > a.left) out.add(new Rect(a.left, t, b.left, bo));
        if (b.right < a.right) out.add(new Rect(b.right, t, a.right, bo));
        return out;
    }

    static List<Rect> subtractAll(List<Rect> list, Rect b) {
        List<Rect> out = new ArrayList<>();
        for (Rect a : list) {
            for (Rect r : subtract(a, b)) {
                if (r.width() > 0 && r.height() > 0) out.add(r);
            }
        }
        return out;
    }
}
