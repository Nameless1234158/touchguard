package com.touchguard.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Configuracoes salvas do app e alguns utilitarios de leitura/escrita. */
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
    /** Zonas personalizadas (retangulos), em coordenadas normalizadas da tela em pe. */
    static final String ZONES = "zones";
    /** Editor de zonas aberto: o servico tira todas as zonas para nao atrapalhar. */
    static final String EDITING = "editing";
    /** App aberto: as zonas personalizadas ficam pausadas para o app continuar tocavel. */
    static final String APP_OPEN = "app_open";
    static final String EDGE_SIDE = "edge_side";
    static final String EDGE_POS = "edge_pos";
    static final String EDGE_H = "edge_h";
    static final String CAL_SECONDS = "cal_seconds";
    static final String CAL_SAMPLES = "cal_samples";
    static final String CAL_TIME = "cal_time";

    static final int EDGE_OFF = 0;
    static final int EDGE_RIGHT = 1;
    static final int EDGE_LEFT = 2;

    /** Largura maxima de cada faixa de borda, em dp. */
    static final int MAX_DP = 48;
    /** Folga, em dp, deixada livre em cima e embaixo das faixas laterais. */
    static final int BAR_GAP_DP = 48;
    /** Tempo maximo do auto-desbloqueio, em minutos. */
    static final int MAX_AUTO_UNLOCK = 120;
    /** Largura da passagem livre para a alca do Painel Edge, em dp. */
    static final int EDGE_GAP_W_DP = 64;
    static final int EDGE_H_MIN = 80;
    static final int EDGE_H_MAX = 400;

    /** Um toque registrado na calibracao. x/y normalizados (0..1) na tela em pe. */
    static final class Sample {
        final float x;
        final float y;
        /** Tamanho do contato, em dp. */
        final float size;
        /** Duracao do toque, em ms. */
        final long dur;

        Sample(float x, float y, float size, long dur) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.dur = dur;
        }
    }

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static boolean isSamsung() {
        return "samsung".equalsIgnoreCase(Build.MANUFACTURER);
    }

    /** Largura da faixa de borda em dp. Padrao: 16 nas laterais, 0 em cima e embaixo. */
    static int zone(Context c, String key) {
        int def = (key.equals(LEFT) || key.equals(RIGHT)) ? 16 : 0;
        return clamp(get(c).getInt(key, def), 0, MAX_DP);
    }

    static int autoUnlock(Context c) {
        return clamp(get(c).getInt(AUTO_UNLOCK, 30), 0, MAX_AUTO_UNLOCK);
    }

    static boolean spareBars(Context c) {
        return get(c).getBoolean(SPARE_BARS, true);
    }

    /** Lado da alca do Painel Edge. Em aparelhos Samsung, comeca na direita. */
    static int edgeSide(Context c) {
        return get(c).getInt(EDGE_SIDE, isSamsung() ? EDGE_RIGHT : EDGE_OFF);
    }

    /** Centro da passagem, em % da altura da tela (0 = topo, 100 = base). */
    static int edgePos(Context c) {
        return clamp(get(c).getInt(EDGE_POS, 30), 0, 100);
    }

    static int edgeH(Context c) {
        return clamp(get(c).getInt(EDGE_H, 220), EDGE_H_MIN, EDGE_H_MAX);
    }

    static int calSeconds(Context c) {
        return get(c).getInt(CAL_SECONDS, 60);
    }

    static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------- Zonas personalizadas ----------

    static List<RectF> zones(Context c) {
        List<RectF> out = new ArrayList<>();
        String raw = get(c).getString(ZONES, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String item : raw.split(";")) {
            String[] p = item.split(",");
            if (p.length != 4) continue;
            try {
                RectF r = new RectF(Float.parseFloat(p[0]), Float.parseFloat(p[1]),
                        Float.parseFloat(p[2]), Float.parseFloat(p[3]));
                if (r.width() > 0 && r.height() > 0) out.add(r);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    static void saveZones(Context c, List<RectF> list) {
        StringBuilder sb = new StringBuilder();
        for (RectF r : list) {
            if (sb.length() > 0) sb.append(';');
            sb.append(String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", r.left, r.top, r.right, r.bottom));
        }
        get(c).edit().putString(ZONES, sb.toString()).apply();
    }

    // ---------- Resultado da calibracao ----------

    static List<Sample> samples(Context c) {
        List<Sample> out = new ArrayList<>();
        String raw = get(c).getString(CAL_SAMPLES, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String item : raw.split(";")) {
            String[] p = item.split(",");
            if (p.length != 4) continue;
            try {
                out.add(new Sample(Float.parseFloat(p[0]), Float.parseFloat(p[1]),
                        Float.parseFloat(p[2]), Long.parseLong(p[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    static void saveSamples(Context c, List<Sample> list) {
        StringBuilder sb = new StringBuilder();
        for (Sample s : list) {
            if (sb.length() > 0) sb.append(';');
            sb.append(String.format(Locale.US, "%.4f,%.4f,%.1f,%d", s.x, s.y, s.size, s.dur));
        }
        get(c).edit()
                .putString(CAL_SAMPLES, sb.toString())
                .putLong(CAL_TIME, System.currentTimeMillis())
                .apply();
    }

    // ---------- Estado do servico ----------

    /** Consulta o sistema, nao so a memoria do app (que some se o processo for encerrado). */
    static boolean serviceEnabled(Context c) {
        if (GuardService.instance != null) return true;
        String s = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null || s.isEmpty()) return false;
        ComponentName me = new ComponentName(c, GuardService.class);
        for (String part : s.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (me.equals(cn)) return true;
        }
        return false;
    }
}
