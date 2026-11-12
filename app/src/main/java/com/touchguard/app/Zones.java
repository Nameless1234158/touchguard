package com.touchguard.app;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/** Calcula, em pixels da tela atual, todas as areas que devem ignorar toques. */
final class Zones {
    private Zones() {}

    static final class Layout {
        /** Retangulos finais bloqueados (faixas + zonas, ja sem a passagem do Painel Edge). */
        final List<Rect> blocked = new ArrayList<>();
        /** So as faixas de borda, para desenhar no editor. */
        final List<Rect> strips = new ArrayList<>();
        /** Passagem livre para a alca do Painel Edge (ou null). */
        Rect edgeGap;
    }

    static Layout compute(Context c, int w, int h, int rot, boolean includeCustom) {
        float d = c.getResources().getDisplayMetrics().density;
        Layout out = new Layout();

        int left = Math.round(Prefs.zone(c, Prefs.LEFT) * d);
        int right = Math.round(Prefs.zone(c, Prefs.RIGHT) * d);
        int top = Math.round(Prefs.zone(c, Prefs.TOP) * d);
        int bottom = Math.round(Prefs.zone(c, Prefs.BOTTOM) * d);

        List<Rect> sides = new ArrayList<>();
        if (left > 0) sides.add(new Rect(0, 0, left, h));
        if (right > 0) sides.add(new Rect(w - right, 0, w, h));
        if (Prefs.spareBars(c)) {
            int gap = Math.round(Prefs.BAR_GAP_DP * d);
            sides = Geo.subtractAll(sides, new Rect(0, 0, w, gap));
            sides = Geo.subtractAll(sides, new Rect(0, h - gap, w, h));
        }
        out.strips.addAll(sides);
        if (top > 0) out.strips.add(new Rect(0, 0, w, top));
        if (bottom > 0) out.strips.add(new Rect(0, h - bottom, w, h));

        List<Rect> all = new ArrayList<>(out.strips);
        if (includeCustom) {
            for (RectF n : Prefs.zones(c)) all.add(Geo.naturalToScreen(n, rot, w, h));
        }

        int edge = Prefs.edgeSide(c);
        if (edge != Prefs.EDGE_OFF) {
            int gw = Math.round(Prefs.EDGE_GAP_W_DP * d);
            int gh = Math.min(h, Math.round(Prefs.edgeH(c) * d));
            int cy = Math.round(h * Prefs.edgePos(c) / 100f);
            int t = Math.max(0, cy - gh / 2);
            int b = Math.min(h, t + gh);
            t = Math.max(0, b - gh);
            out.edgeGap = edge == Prefs.EDGE_RIGHT ? new Rect(w - gw, t, w, b) : new Rect(0, t, gw, b);
            all = Geo.subtractAll(all, out.edgeGap);
        }

        for (Rect r : all) {
            if (r.width() > 0 && r.height() > 0) out.blocked.add(r);
        }
        return out;
    }
}
