package com.touchguard.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private TextView status;
    private Switch lockSwitch;
    private SharedPreferences prefs;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        // Margem lateral maior que a faixa maxima, para o app sempre continuar tocavel.
        int side = dp(Prefs.MAX_DP + 8);
        col.setPadding(side, dp(24), side, dp(32));
        scroll.addView(col);

        TextView intro = new TextView(this);
        intro.setText("O TouchGuard cria faixas invisíveis nas bordas da tela que ignoram "
                + "qualquer toque. Ajuda quando os toques fantasmas aparecem sempre nas "
                + "mesmas regiões. Ele não conserta o defeito da tela, só reduz o estrago.\n\n"
                + "1) Toque no botão abaixo, abra \"Apps instalados\" ou \"Serviços baixados\" "
                + "e ative o TouchGuard.\n"
                + "2) Ajuste as faixas. Ligue \"Mostrar zonas\" para ver onde elas ficam.\n\n"
                + "Se o Android não deixar ativar (configuração restrita): Configurações > "
                + "Apps > TouchGuard > menu de três pontos > Permitir configurações restritas.");
        col.addView(intro);

        status = new TextView(this);
        status.setPadding(0, dp(16), 0, dp(8));
        col.addView(status);

        Button open = new Button(this);
        open.setText("Abrir configurações de Acessibilidade");
        open.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        col.addView(open);

        addSlider(col, "Borda esquerda", Prefs.LEFT, Prefs.MAX_DP, " dp", Prefs.zone(this, Prefs.LEFT));
        addSlider(col, "Borda direita", Prefs.RIGHT, Prefs.MAX_DP, " dp", Prefs.zone(this, Prefs.RIGHT));
        addSlider(col, "Borda de cima", Prefs.TOP, Prefs.MAX_DP, " dp", Prefs.zone(this, Prefs.TOP));
        addSlider(col, "Borda de baixo", Prefs.BOTTOM, Prefs.MAX_DP, " dp", Prefs.zone(this, Prefs.BOTTOM));

        Switch spare = new Switch(this);
        spare.setText("Faixas laterais não cobrem a barra de status nem a barra de gestos");
        spare.setPadding(0, dp(16), 0, dp(8));
        spare.setChecked(Prefs.spareBars(this));
        spare.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean(Prefs.SPARE_BARS, on).apply());
        col.addView(spare);

        Switch show = new Switch(this);
        show.setText("Mostrar zonas bloqueadas (vermelho)");
        show.setPadding(0, dp(8), 0, dp(8));
        show.setChecked(prefs.getBoolean(Prefs.SHOW, false));
        show.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean(Prefs.SHOW, on).apply());
        col.addView(show);

        lockSwitch = new Switch(this);
        lockSwitch.setText("Travar a tela toda (segure 2s no botão para liberar)");
        lockSwitch.setPadding(0, dp(8), 0, dp(8));
        lockSwitch.setOnCheckedChangeListener((b, on) -> {
            if (on && !GuardService.running) {
                b.setChecked(false);
                status.setText("Ative o serviço na Acessibilidade antes de travar a tela.");
                return;
            }
            prefs.edit().putBoolean(Prefs.LOCK, on).apply();
        });
        col.addView(lockSwitch);

        addSlider(col, "Liberar a trava sozinho após (0 = nunca)", Prefs.AUTO_UNLOCK,
                Prefs.MAX_AUTO_UNLOCK, " min", Prefs.autoUnlock(this));

        TextView tip = new TextView(this);
        tip.setText("Dica: adicione o botão \"Travar toques\" ao painel rápido para travar "
                + "a tela toda (por exemplo, ao assistir vídeo) sem abrir o app. "
                + "Ao reiniciar o celular, a trava total começa sempre desligada.");
        tip.setPadding(0, dp(16), 0, 0);
        col.addView(tip);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefs.registerOnSharedPreferenceChangeListener(this);
        refreshState();
    }

    @Override
    protected void onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Prefs.LOCK.equals(key)) refreshState();
    }

    private void refreshState() {
        boolean on = isServiceEnabled();
        status.setText(on
                ? "Serviço: ATIVO"
                : "Serviço: desativado (ative na Acessibilidade)");
        lockSwitch.setChecked(on && prefs.getBoolean(Prefs.LOCK, false));
    }

    private boolean isServiceEnabled() {
        if (GuardService.running) return true;
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName());
    }

    private void addSlider(LinearLayout col, final String label, final String key,
                           int max, final String unit, int cur) {
        final TextView tv = new TextView(this);
        tv.setPadding(0, dp(16), 0, 0);
        tv.setText(label + ": " + cur + unit);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(cur);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                tv.setText(label + ": " + value + unit);
                if (fromUser) {
                    prefs.edit().putInt(key, value).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });

        col.addView(tv);
        col.addView(sb);
    }
}
