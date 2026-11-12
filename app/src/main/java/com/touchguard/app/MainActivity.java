package com.touchguard.app;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.util.List;

public class MainActivity extends Activity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private interface IntPick {
        void pick(int index);
    }

    private SharedPreferences prefs;
    private Ui ui;
    private ScrollView scroll;
    private LinearLayout col;
    private final Runnable renderTask = this::render;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        ui = new Ui(this);

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(ui.bg);
        scroll.setFillViewport(true);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col);
        setContentView(scroll);

        getWindow().setStatusBarColor(ui.bg);
        getWindow().setNavigationBarColor(ui.bg);
        if (!ui.night) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Com o app aberto, as zonas personalizadas ficam pausadas (o app nunca fica bloqueado).
        prefs.edit().putBoolean(Prefs.APP_OPEN, true).apply();
        prefs.registerOnSharedPreferenceChangeListener(this);
        render();
    }

    @Override
    protected void onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        prefs.edit().putBoolean(Prefs.APP_OPEN, false).apply();
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Prefs.LOCK.equals(key) || Prefs.ZONES.equals(key) || Prefs.CAL_TIME.equals(key)) {
            col.removeCallbacks(renderTask);
            col.post(renderTask);
        } else if (Prefs.LEFT.equals(key) || Prefs.RIGHT.equals(key)) {
            updatePadding();
        }
    }

    /** Margem lateral sempre maior que as faixas, para o app continuar tocavel. */
    private void updatePadding() {
        int side = Math.max(16, Math.max(Prefs.zone(this, Prefs.LEFT), Prefs.zone(this, Prefs.RIGHT)) + 10);
        col.setPadding(ui.dp(side), ui.dp(20), ui.dp(side), ui.dp(40));
    }

    private void render() {
        final int y = scroll.getScrollY();
        col.removeAllViews();
        updatePadding();
        header();
        statusCard();
        calibrationCard();
        zonesCard();
        edgeCard();
        lockCard();
        footer();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    // ---------- Secoes ----------

    private void header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(4), ui.dp(8), 0, ui.dp(6));

        TextView icon = ui.label("🛡️", 26, ui.text, false);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(ui.round(ui.accentSoft, 16));
        row.addView(icon, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(ui.dp(14), 0, 0, 0);
        titles.addView(ui.label("TouchGuard", 24, ui.text, true));
        titles.addView(ui.label("Proteção contra toques fantasmas", 14, ui.sub, false));
        row.addView(titles);
        col.addView(row);
    }

    private void statusCard() {
        boolean on = Prefs.serviceEnabled(this);
        LinearLayout card = ui.card();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? ui.ok : ui.bad);
        dot.setBackground(g);
        row.addView(dot, new LinearLayout.LayoutParams(ui.dp(12), ui.dp(12)));
        TextView t = ui.label(on ? "Proteção ativa" : "Serviço desligado", 18, ui.text, true);
        t.setPadding(ui.dp(10), 0, 0, 0);
        row.addView(t);
        card.addView(row);

        card.addView(sub(on
                ? "As zonas vermelhas estão ignorando toques em todos os apps. "
                + "Com o TouchGuard aberto, as zonas desenhadas ficam pausadas para você mexer aqui."
                : "Ative o TouchGuard na Acessibilidade: abra \"Apps instalados\" (ou \"Serviços "
                + "baixados\") e ligue o TouchGuard. Se o Android bloquear, vá em Configurações > "
                + "Apps > TouchGuard > ⋮ > Permitir configurações restritas."));

        TextView open = ui.button(on ? "Configurações de Acessibilidade" : "Ativar na Acessibilidade", !on);
        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        card.addView(open, ui.matchWrap(14));
        col.addView(card);
    }

    private void calibrationCard() {
        LinearLayout card = ui.card();
        card.addView(title("🎯  Encontrar os toques fantasmas"));
        card.addView(sub("Deixe o celular parado por um tempo. O TouchGuard registra onde os toques "
                + "falsos aparecem (posição, duração e tamanho do contato), mostra um mapa de calor "
                + "e sugere as zonas de bloqueio certas."));

        int sec = Prefs.calSeconds(this);
        int sel = sec <= 30 ? 0 : sec <= 60 ? 1 : 2;
        card.addView(segmented(new String[]{"30 s", "1 min", "2 min"}, sel, i ->
                prefs.edit().putInt(Prefs.CAL_SECONDS, i == 0 ? 30 : i == 1 ? 60 : 120).apply()), ui.matchWrap(14));

        TextView start = ui.button("Iniciar calibração", true);
        start.setOnClickListener(v -> startCalibration());
        card.addView(start, ui.matchWrap(12));

        long when = prefs.getLong(Prefs.CAL_TIME, 0);
        if (when > 0) {
            List<Prefs.Sample> s = Prefs.samples(this);
            int regions = Suggest.fromSamples(s).size();
            long avgDur = 0;
            float avgSize = 0;
            for (Prefs.Sample p : s) {
                avgDur += p.dur;
                avgSize += p.size;
            }
            String stats;
            if (s.isEmpty()) {
                stats = "Nenhum toque fantasma apareceu.";
            } else {
                avgDur /= s.size();
                avgSize /= s.size();
                stats = s.size() + " toque(s) em " + regions + " região(ões) · duração média "
                        + avgDur + " ms · contato médio " + Math.round(avgSize) + " dp";
            }
            String date = DateFormat.getDateFormat(this).format(new Date(when)) + " "
                    + DateFormat.getTimeFormat(this).format(new Date(when));
            TextView last = ui.label("Última calibração (" + date + "): " + stats, 13, ui.sub, false);
            last.setPadding(0, ui.dp(12), 0, 0);
            card.addView(last);

            TextView see = ui.button("Ver resultado e sugestões", false);
            see.setOnClickListener(v -> startActivity(new Intent(this, ZoneEditorActivity.class)
                    .putExtra(ZoneEditorActivity.EXTRA_FROM_CAL, true)));
            card.addView(see, ui.matchWrap(10));
        }
        col.addView(card);
    }

    private void zonesCard() {
        LinearLayout card = ui.card();
        card.addView(title("🟥  Zonas bloqueadas"));
        int n = Prefs.zones(this).size();
        card.addView(sub(n == 0
                ? "Nenhuma zona desenhada. Desenhe retângulos em qualquer parte da tela, "
                + "ou use a calibração para criar as zonas automaticamente."
                : n + " zona(s) desenhada(s) na tela."));

        TextView edit = ui.button("Editar zonas na tela", true);
        edit.setOnClickListener(v -> startActivity(new Intent(this, ZoneEditorActivity.class)));
        card.addView(edit, ui.matchWrap(14));

        card.addView(switchRow("Mostrar zonas bloqueadas",
                "Pinta as zonas de vermelho e a passagem do Painel Edge de verde, por cima dos apps.",
                prefs.getBoolean(Prefs.SHOW, false),
                on -> prefs.edit().putBoolean(Prefs.SHOW, on).apply()));

        card.addView(divider());
        card.addView(caption("FAIXAS NAS BORDAS"));
        card.addView(slider("Esquerda", Prefs.LEFT, 0, Prefs.MAX_DP, Prefs.zone(this, Prefs.LEFT), " dp"));
        card.addView(slider("Direita", Prefs.RIGHT, 0, Prefs.MAX_DP, Prefs.zone(this, Prefs.RIGHT), " dp"));
        card.addView(slider("Em cima", Prefs.TOP, 0, Prefs.MAX_DP, Prefs.zone(this, Prefs.TOP), " dp"));
        card.addView(slider("Embaixo", Prefs.BOTTOM, 0, Prefs.MAX_DP, Prefs.zone(this, Prefs.BOTTOM), " dp"));
        card.addView(switchRow("Liberar barra de status e de gestos",
                "As faixas laterais não cobrem o topo nem a base da tela.",
                Prefs.spareBars(this),
                on -> prefs.edit().putBoolean(Prefs.SPARE_BARS, on).apply()));
        col.addView(card);
    }

    private void edgeCard() {
        LinearLayout card = ui.card();
        card.addView(title("📑  Alça do Painel Edge"));
        card.addView(sub("Deixa uma passagem livre na lateral para você continuar puxando o Painel "
                + "Edge (Samsung) mesmo com as faixas ligadas. Ligue \"Mostrar zonas bloqueadas\" "
                + "e ajuste até a área verde ficar em cima da alça."));

        int side = Prefs.edgeSide(this);
        int sel = side == Prefs.EDGE_RIGHT ? 0 : side == Prefs.EDGE_LEFT ? 1 : 2;
        card.addView(segmented(new String[]{"Direita", "Esquerda", "Desligado"}, sel, i ->
                prefs.edit().putInt(Prefs.EDGE_SIDE,
                        i == 0 ? Prefs.EDGE_RIGHT : i == 1 ? Prefs.EDGE_LEFT : Prefs.EDGE_OFF).apply()),
                ui.matchWrap(14));
        card.addView(slider("Posição (0 = topo, 100 = base)", Prefs.EDGE_POS, 0, 100,
                Prefs.edgePos(this), "%"));
        card.addView(slider("Altura da passagem", Prefs.EDGE_H, Prefs.EDGE_H_MIN, Prefs.EDGE_H_MAX,
                Prefs.edgeH(this), " dp"));
        col.addView(card);
    }

    private void lockCard() {
        LinearLayout card = ui.card();
        card.addView(title("🔒  Trava total"));
        card.addView(sub("Ignora toques na tela inteira, por exemplo ao assistir um vídeo. "
                + "Para liberar, segure o botão na tela por 2 segundos."));

        card.addView(switchRow("Travar a tela toda", null,
                Prefs.serviceEnabled(this) && prefs.getBoolean(Prefs.LOCK, false),
                on -> {
                    if (on && GuardService.instance == null) {
                        toast("Ative o serviço na Acessibilidade antes de travar a tela.");
                        col.post(renderTask);
                        return;
                    }
                    prefs.edit().putBoolean(Prefs.LOCK, on).apply();
                }));
        card.addView(slider("Liberar sozinho após (0 = nunca)", Prefs.AUTO_UNLOCK, 0,
                Prefs.MAX_AUTO_UNLOCK, Prefs.autoUnlock(this), " min"));

        TextView tile = ui.button(Build.VERSION.SDK_INT >= 33
                ? "Adicionar \"Travar toques\" ao painel rápido"
                : "Como adicionar ao painel rápido", false);
        tile.setOnClickListener(v -> addTile());
        card.addView(tile, ui.matchWrap(14));
        col.addView(card);
    }

    private void footer() {
        TextView t = ui.label("O TouchGuard não conserta a tela: ele só ignora os toques nas áreas "
                + "escolhidas. Toques fantasmas no meio da tela só podem ser bloqueados junto com "
                + "os toques de verdade naquela área. Ao reiniciar o celular, a trava total começa "
                + "sempre desligada.", 12, ui.sub, false);
        t.setPadding(ui.dp(6), ui.dp(18), ui.dp(6), 0);
        col.addView(t);
    }

    // ---------- Acoes ----------

    private void startCalibration() {
        if (!Prefs.serviceEnabled(this)) {
            toast("Ative o TouchGuard na Acessibilidade primeiro.");
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        GuardService s = GuardService.instance;
        if (s == null) {
            toast("O serviço ainda está iniciando. Tente de novo em alguns segundos.");
            return;
        }
        if (!s.startCalibration(Prefs.calSeconds(this))) {
            toast("Não foi possível iniciar a calibração.");
        }
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager sbm = getSystemService(StatusBarManager.class);
            if (sbm != null) {
                sbm.requestAddTileService(new ComponentName(this, LockTile.class),
                        getString(R.string.tile_label),
                        Icon.createWithResource(this, R.drawable.ic_tile),
                        getMainExecutor(),
                        result -> {
                            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                                toast("O botão já está no painel rápido.");
                            }
                        });
                return;
            }
        }
        toast("Puxe o painel rápido, toque no lápis (editar) e arraste \"Travar toques\" para os botões ativos.");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ---------- Componentes ----------

    private TextView title(String s) {
        return ui.label(s, 17, ui.text, true);
    }

    private TextView sub(String s) {
        TextView t = ui.label(s, 14, ui.sub, false);
        t.setPadding(0, ui.dp(6), 0, 0);
        return t;
    }

    private TextView caption(String s) {
        TextView t = ui.label(s, 12, ui.sub, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, ui.dp(4), 0, 0);
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(ui.line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, ui.dp(1)));
        lp.topMargin = ui.dp(16);
        lp.bottomMargin = ui.dp(12);
        v.setLayoutParams(lp);
        return v;
    }

    private interface BoolChange {
        void change(boolean on);
    }

    private View switchRow(String label, String hint, boolean checked, BoolChange cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(14), 0, 0);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(ui.label(label, 15, ui.text, false));
        if (hint != null) texts.addView(ui.label(hint, 12, ui.sub, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{ui.accent, ui.night ? 0xFFBDBDBD : 0xFFFAFAFA}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{ui.accent, ui.night ? 0xFF555A66 : 0xFFC4C8D0}));
        sw.setOnCheckedChangeListener((b, on) -> cb.change(on));
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }

    private View slider(String label, String key, int min, int max, int cur, String unit) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, ui.dp(12), 0, 0);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = ui.label(label, 14, ui.text, false);
        top.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        final TextView value = ui.label(cur + unit, 14, ui.accent, true);
        top.addView(value);
        box.addView(top);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(cur - min);
        sb.setProgressTintList(ColorStateList.valueOf(ui.accent));
        sb.setThumbTintList(ColorStateList.valueOf(ui.accent));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = p + min;
                value.setText(v + unit);
                if (fromUser) prefs.edit().putInt(key, v).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        box.addView(sb, ui.matchWrap(4));
        return box;
    }

    private View segmented(String[] labels, int selected, IntPick cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(ui.round(ui.chip, 14));
        row.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        final TextView[] items = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = ui.label(labels[i], 14, ui.text, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, ui.dp(9), 0, ui.dp(9));
            t.setClickable(true);
            t.setOnClickListener(v -> {
                for (int k = 0; k < items.length; k++) styleSeg(items[k], k == idx);
                cb.pick(idx);
            });
            items[i] = t;
            styleSeg(t, i == selected);
            row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        return row;
    }

    private void styleSeg(TextView t, boolean on) {
        t.setBackground(on ? ui.round(ui.accent, 11) : null);
        t.setTextColor(on ? ui.onAccent : ui.text);
    }
}
