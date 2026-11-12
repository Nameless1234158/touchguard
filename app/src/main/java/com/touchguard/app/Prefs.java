package com.touchguard.app;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String NAME = "touchguard";
    static final String LEFT = "left";
    static final String RIGHT = "right";
    static final String TOP = "top";
    static final String BOTTOM = "bottom";
    static final String SHOW = "show";
    static final String LOCK = "lock";
    static final String SPARE_BARS = "spare_bars";
    static final String AUTO_UNLOCK = "auto_unlock_min";

    /** Largura maxima de cada faixa, em dp. O app usa margem maior que isso. */
    static final int MAX_DP = 48;
    /** Folga, em dp, deixada livre em cima e embaixo das faixas laterais. */
    static final int BAR_GAP_DP = 48;
    /** Tempo maximo do auto-desbloqueio, em minutos. */
    static final int MAX_AUTO_UNLOCK = 120;

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** Largura da faixa bloqueada em dp. Padrao: 20 nas laterais, 0 em cima e embaixo. */
    static int zone(Context c, String key) {
        int def = (key.equals(LEFT) || key.equals(RIGHT)) ? 20 : 0;
        return Math.min(get(c).getInt(key, def), MAX_DP);
    }

    /** Minutos ate a trava total se soltar sozinha. 0 = nunca. Padrao: 30. */
    static int autoUnlock(Context c) {
        return get(c).getInt(AUTO_UNLOCK, 30);
    }

    static boolean spareBars(Context c) {
        return get(c).getBoolean(SPARE_BARS, true);
    }
}
