package com.touchguard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor de zonas em tela cheia. Mostra o mapa de calor da ultima calibracao,
 * as zonas sugeridas (tracejado laranja), as zonas salvas (vermelho), as faixas
 * de borda e a passagem do Painel Edge (verde).
 * Desenhar: arraste para criar uma zona; toque numa sugestao para aceita-la.
 * Apagar: toque numa zona ou sugestao para remove-la.
 */
public class ZoneEditorActivity extends Activity {

    static final String EXTRA_FROM_CAL = "from_cal";

    private Ui ui;
    private final List<RectF> zones = new ArrayList<>();
    private final List<RectF> suggestions = new ArrayList<>();
    private List<Prefs.Sample> samples = new ArrayList<>();
    private boolean eraseMode;
    private boolean changed;
    private int barPos = 2; // 0 = topo, 1 = meio, 2 = base

    private EditorView editor;
    private LinearLayout bar;
    private TextView hint, drawBtn, eraseBtn, sugBtn;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this, true);

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams a = w.getAttributes();
            a.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(a);
        }

        zones.addAll(Prefs.zones(this));
        samples = Prefs.samples(this);
        boolean fromCal = getIntent().getBooleanExtra(EXTRA_FROM_CAL, false);
        if (fromCal) {
            suggestions.addAll(Suggest.fromSamples(samples));
            if (!suggestions.isEmpty()) changed = true;
        }

        FrameLayout root = new FrameLayout(this);
        editor = new EditorView(this);
        root.addView(editor, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        bar = buildBar();
        root.addView(bar);
        placeBar();
        setContentView(root);

        if (fromCal) {
            if (samples.isEmpty()) {
                hint.setText("Nenhum toque fantasma apareceu na calibração. Tente de novo com mais "
                        + "tempo, ou desenhe as zonas arrastando o dedo.");
            } else {
                hint.setText(samples.size() + " toque(s) fantasma(s) em " + suggestions.size()
                        + " região(ões). Toque em \"Aplicar sugestões\" ou toque em cada área "
                        + "laranja para aceitar só ela.");
            }
        }
        updateLabels();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        // Enquanto o editor esta aberto, o servico tira as zonas da frente.
        Prefs.get(this).edit().putBoolean(Prefs.EDITING, true).apply();
    }

    @Override
    protected void onPause() {
        Prefs.get(this).edit().putBoolean(Prefs.EDITING, false).apply();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (!changed) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Salvar as zonas?")
                .setMessage("Você fez mudanças que ainda não foram salvas.")
                .setPositiveButton("Salvar", (d, i) -> save())
                .setNegativeButton("Descartar", (d, i) -> finish())
                .setNeutralButton("Continuar editando", null)
                .show();
    }

    // ---------- Barra de ferramentas ----------

    private LinearLayout buildBar() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setBackground(ui.round(0xF21A1D24, 22));
        b.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(14));
        b.setClickable(true); // toques na barra nao vazam para o desenho

        hint = ui.label("Arraste o dedo para desenhar uma zona. Áreas vermelhas ignoram toques.",
                13, ui.sub, false);
        b.addView(hint);

        LinearLayout row1 = row();
        drawBtn = chip("✏️ Desenhar");
        eraseBtn = chip("🧽 Apagar");
        TextView move = chip("⇅");
        drawBtn.setOnClickListener(v -> {
            eraseMode = false;
            updateLabels();
        });
        eraseBtn.setOnClickListener(v -> {
            eraseMode = true;
            updateLabels();
        });
        move.setOnClickListener(v -> {
            barPos = (barPos + 1) % 3;
            placeBar();
        });
        row1.addView(drawBtn, weight(1));
        row1.addView(eraseBtn, weight(1));
        row1.addView(move, weight(0));
        b.addView(row1);

        LinearLayout row2 = row();
        sugBtn = ui.button("Aplicar sugestões", false);
        sugBtn.setOnClickListener(v -> {
            if (suggestions.isEmpty()) {
                Toast.makeText(this, "Sem sugestões. Faça uma calibração na tela inicial.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            zones.addAll(suggestions);
            suggestions.clear();
            changed = true;
            updateLabels();
            editor.invalidate();
        });
        TextView clear = ui.button("Limpar tudo", false);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Apagar todas as zonas?")
                .setMessage("As faixas de borda continuam; só as zonas desenhadas são apagadas.")
                .setPositiveButton("Apagar", (d, i) -> {
                    zones.clear();
                    changed = true;
                    updateLabels();
                    editor.invalidate();
                })
                .setNegativeButton("Cancelar", null)
                .show());
        row2.addView(sugBtn, weight(1));
        row2.addView(clear, weight(1));
        b.addView(row2);

        LinearLayout row3 = row();
        TextView cancel = ui.button("Cancelar", false);
        cancel.setOnClickListener(v -> finish());
        TextView ok = ui.button("Salvar", true);
        ok.setOnClickListener(v -> save());
        row3.addView(cancel, weight(1));
        row3.addView(ok, weight(1));
        b.addView(row3);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = ui.matchWrap(10);
        r.setLayoutParams(lp);
        return r;
    }

    private LinearLayout.LayoutParams weight(float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                w == 0 ? LinearLayout.LayoutParams.WRAP_CONTENT : 0,
                LinearLayout.LayoutParams.WRAP_CONTENT, w);
        lp.leftMargin = ui.dp(4);
        lp.rightMargin = ui.dp(4);
        return lp;
    }

    private TextView chip(String s) {
        TextView t = ui.label(s, 14, ui.text, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(ui.dp(14), ui.dp(11), ui.dp(14), ui.dp(11));
        t.setClickable(true);
        return t;
    }

    private void placeBar() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        int g = barPos == 0 ? Gravity.TOP : barPos == 1 ? Gravity.CENTER_VERTICAL : Gravity.BOTTOM;
        lp.gravity = g;
        int m = ui.dp(Prefs.MAX_DP + 12);
        lp.setMargins(m, ui.dp(36), m, ui.dp(36));
        bar.setLayoutParams(lp);
    }

    private void updateLabels() {
        drawBtn.setBackground(ui.round(eraseMode ? ui.chip : ui.accent, 14));
        drawBtn.setTextColor(eraseMode ? ui.text : ui.onAccent);
        eraseBtn.setBackground(ui.round(eraseMode ? ui.accent : ui.chip, 14));
        eraseBtn.setTextColor(eraseMode ? ui.onAccent : ui.text);
        sugBtn.setText(suggestions.isEmpty() ? "Aplicar sugestões"
                : "Aplicar sugestões (" + suggestions.size() + ")");
        sugBtn.setAlpha(suggestions.isEmpty() ? 0.5f : 1f);
    }

    private void save() {
        Prefs.saveZones(this, zones);
        changed = false;
        Toast.makeText(this, zones.size() + " zona(s) salva(s)", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---------- Area de desenho ----------

    private final class EditorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] loc = new int[2];
        private Point screen = new Point(1, 1);
        private float downX, downY, curX, curY;
        private boolean tracking, dragging;

        EditorView(Context c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            dash.setStyle(Paint.Style.STROKE);
            dash.setStrokeWidth(ui.dp(2));
            dash.setPathEffect(new DashPathEffect(new float[]{ui.dp(8), ui.dp(6)}, 0));
            txt.setColor(0xFFFFFFFF);
            txt.setTextSize(ui.dp(12));
        }

        @Override
        protected void onLayout(boolean ch, int l, int t, int r, int b) {
            super.onLayout(ch, l, t, r, b);
            getLocationOnScreen(loc);
            screen = Geo.screenSize(getWindowManager());
        }

        private float vx(float nx) {
            return nx * screen.x - loc[0];
        }

        private float vy(float ny) {
            return ny * screen.y - loc[1];
        }

        private float nx(float x) {
            return Math.max(0f, Math.min(1f, (x + loc[0]) / (float) screen.x));
        }

        private float ny(float y) {
            return Math.max(0f, Math.min(1f, (y + loc[1]) / (float) screen.y));
        }

        private RectF toView(RectF n) {
            return new RectF(vx(n.left), vy(n.top), vx(n.right), vy(n.bottom));
        }

        private RectF toView(Rect s) {
            return new RectF(s.left - loc[0], s.top - loc[1], s.right - loc[0], s.bottom - loc[1]);
        }

        @Override
        protected void onDraw(Canvas cv) {
            cv.drawColor(0xFF0E1014);
            float r = ui.dp(10);

            // Grade de referencia.
            stroke.setStrokeWidth(1);
            stroke.setColor(0x14FFFFFF);
            for (int i = 1; i < Suggest.GX; i++) {
                float x = vx(i / (float) Suggest.GX);
                cv.drawLine(x, 0, x, getHeight(), stroke);
            }
            for (int i = 1; i < Suggest.GY; i++) {
                float y = vy(i / (float) Suggest.GY);
                cv.drawLine(0, y, getWidth(), y, stroke);
            }

            // Faixas de borda e passagem do Painel Edge (configuradas na tela inicial).
            Zones.Layout layout = Zones.compute(getContext(), screen.x, screen.y, 0, false);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x33FF3B30);
            for (Rect s : layout.strips) cv.drawRect(toView(s), fill);
            if (layout.edgeGap != null) {
                RectF g = toView(layout.edgeGap);
                fill.setColor(0x4434C759);
                cv.drawRect(g, fill);
                stroke.setStrokeWidth(ui.dp(2));
                stroke.setColor(0xFF34C759);
                cv.drawRect(g, stroke);
                cv.save();
                cv.rotate(-90, g.centerX(), g.centerY());
                txt.setTextAlign(Paint.Align.CENTER);
                cv.drawText("Painel Edge", g.centerX(), g.centerY() + ui.dp(4), txt);
                cv.restore();
            }

            // Zonas salvas/aceitas.
            for (RectF z : zones) {
                RectF v = toView(z);
                fill.setColor(0x66FF3B30);
                cv.drawRoundRect(v, r, r, fill);
                stroke.setStrokeWidth(ui.dp(2));
                stroke.setColor(0xFFFF3B30);
                cv.drawRoundRect(v, r, r, stroke);
            }

            // Sugestoes da calibracao.
            for (RectF s : suggestions) {
                RectF v = toView(s);
                fill.setColor(0x33FF9F0A);
                cv.drawRoundRect(v, r, r, fill);
                dash.setColor(0xFFFF9F0A);
                cv.drawRoundRect(v, r, r, dash);
                txt.setTextAlign(Paint.Align.LEFT);
                cv.drawText(Suggest.countIn(samples, s) + " toque(s)",
                        v.left + ui.dp(6), v.top + ui.dp(16), txt);
            }

            // Mapa de calor: cada toque fantasma registrado.
            for (Prefs.Sample s : samples) {
                float x = vx(s.x), y = vy(s.y);
                fill.setColor(0x40FFD60A);
                cv.drawCircle(x, y, ui.dp(16), fill);
                fill.setColor(0xFFFFD60A);
                cv.drawCircle(x, y, ui.dp(4), fill);
            }

            // Zona sendo desenhada agora.
            if (dragging && !eraseMode) {
                RectF v = new RectF(Math.min(downX, curX), Math.min(downY, curY),
                        Math.max(downX, curX), Math.max(downY, curY));
                fill.setColor(0x33FFFFFF);
                cv.drawRect(v, fill);
                dash.setColor(0xFFFFFFFF);
                cv.drawRect(v, dash);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (ev.getPointerCount() > 1) {
                tracking = false;
                dragging = false;
                invalidate();
                return true;
            }
            float x = ev.getX(), y = ev.getY();
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tracking = true;
                    dragging = false;
                    downX = curX = x;
                    downY = curY = y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!tracking) break;
                    curX = x;
                    curY = y;
                    if (Math.hypot(curX - downX, curY - downY) > ui.dp(12)) dragging = true;
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    if (!tracking) break;
                    tracking = false;
                    if (dragging) {
                        dragging = false;
                        if (!eraseMode) addDrawn();
                    } else {
                        tap(x, y);
                    }
                    invalidate();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    tracking = false;
                    dragging = false;
                    invalidate();
                    break;
                default:
                    break;
            }
            return true;
        }

        private void addDrawn() {
            float w = Math.abs(curX - downX), h = Math.abs(curY - downY);
            if (w < ui.dp(16) || h < ui.dp(16)) {
                Toast.makeText(getContext(), "Zona muito pequena", Toast.LENGTH_SHORT).show();
                return;
            }
            zones.add(new RectF(nx(Math.min(downX, curX)), ny(Math.min(downY, curY)),
                    nx(Math.max(downX, curX)), ny(Math.max(downY, curY))));
            changed = true;
        }

        private void tap(float x, float y) {
            float px = nx(x), py = ny(y);
            if (eraseMode) {
                for (int i = zones.size() - 1; i >= 0; i--) {
                    if (zones.get(i).contains(px, py)) {
                        zones.remove(i);
                        changed = true;
                        updateLabels();
                        return;
                    }
                }
                for (int i = suggestions.size() - 1; i >= 0; i--) {
                    if (suggestions.get(i).contains(px, py)) {
                        suggestions.remove(i);
                        updateLabels();
                        return;
                    }
                }
            } else {
                for (int i = suggestions.size() - 1; i >= 0; i--) {
                    if (suggestions.get(i).contains(px, py)) {
                        zones.add(suggestions.remove(i));
                        changed = true;
                        updateLabels();
                        return;
                    }
                }
            }
        }
    }
}
