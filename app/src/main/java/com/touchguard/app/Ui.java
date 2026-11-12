package com.touchguard.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Cores e componentes visuais do app (claro e escuro). */
final class Ui {
    final Context c;
    final boolean night;
    final float density;
    final int bg, card, text, sub, accent, onAccent, accentSoft, ok, bad, line, chip;

    Ui(Context c) {
        this(c, (c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
    }

    Ui(Context c, boolean night) {
        this.c = c;
        this.night = night;
        this.density = c.getResources().getDisplayMetrics().density;
        if (night) {
            bg = 0xFF0F1115;
            card = 0xFF1A1D24;
            text = 0xFFF3F4F6;
            sub = 0xFF9CA3AF;
            accent = 0xFF8B93FF;
            onAccent = 0xFF0F1115;
            accentSoft = 0xFF2A2F45;
            ok = 0xFF22C55E;
            bad = 0xFFF87171;
            line = 0xFF2A2E37;
            chip = 0xFF23262E;
        } else {
            bg = 0xFFF2F3F7;
            card = 0xFFFFFFFF;
            text = 0xFF111827;
            sub = 0xFF6B7280;
            accent = 0xFF4F46E5;
            onAccent = 0xFFFFFFFF;
            accentSoft = 0xFFE0E7FF;
            ok = 0xFF16A34A;
            bad = 0xFFDC2626;
            line = 0xFFE5E7EB;
            chip = 0xFFEEF0F4;
        }
    }

    int dp(float v) {
        return Math.round(v * density);
    }

    GradientDrawable round(int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    Drawable ripple(Drawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(night ? 0x33FFFFFF : 0x22000000),
                content, round(0xFFFFFFFF, radiusDp));
    }

    TextView label(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    LinearLayout card() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(card, 22));
        l.setPadding(dp(18), dp(16), dp(18), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        l.setLayoutParams(lp);
        return l;
    }

    TextView button(String s, boolean primary) {
        TextView b = label(s, 15, primary ? onAccent : accent, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(13), dp(16), dp(13));
        b.setBackground(ripple(round(primary ? accent : accentSoft, 14), 14));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    LinearLayout.LayoutParams matchWrap(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }
}
