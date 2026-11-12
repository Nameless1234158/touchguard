package com.touchguard.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Servico de acessibilidade que desenha janelas transparentes por cima da tela.
 * Toques que caem nessas janelas sao consumidos e nao chegam ao app de baixo.
 * Nao le o conteudo da tela (canRetrieveWindowContent=false).
 */
public class GuardService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final long HOLD_MS = 2000;

    /** Instancia ativa (null quando o servico esta desligado). */
    static volatile GuardService instance;

    private WindowManager wm;
    private SharedPreferences prefs;
    private final List<View> zoneViews = new ArrayList<>();
    private View gapView;
    private View lockView;
    private View calView;
    private boolean calibrating;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoUnlock = this::unlock;
    private final Runnable rebuildTask = this::rebuild;

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = Prefs.get(this);
        // Nunca comecar travado nem em modo de edicao (ex.: depois de reiniciar o celular).
        prefs.edit().putBoolean(Prefs.LOCK, false).putBoolean(Prefs.EDITING, false).commit();
        prefs.registerOnSharedPreferenceChangeListener(this);
        instance = this;
        rebuild();
        LockTile.requestRefresh(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        shutdown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private void shutdown() {
        if (instance == this) instance = null;
        handler.removeCallbacksAndMessages(null);
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            prefs.edit().putBoolean(Prefs.LOCK, false).apply();
        }
        clearZones();
        removeGap();
        removeLock();
        if (calView != null) {
            removeSafe(calView);
            calView = null;
        }
        calibrating = false;
        LockTile.requestRefresh(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Recalcula as zonas ao girar a tela.
        scheduleRebuild();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Prefs.LOCK.equals(key)) {
            // O temporizador so reinicia quando a trava muda, nao a cada ajuste.
            handler.removeCallbacks(autoUnlock);
            if (p.getBoolean(Prefs.LOCK, false)) {
                int min = Prefs.autoUnlock(this);
                if (min > 0) handler.postDelayed(autoUnlock, min * 60_000L);
            }
            LockTile.requestRefresh(this);
            rebuild();
            return;
        }
        if (Prefs.CAL_SAMPLES.equals(key) || Prefs.CAL_TIME.equals(key)
                || Prefs.CAL_SECONDS.equals(key) || Prefs.AUTO_UNLOCK.equals(key)) {
            return;
        }
        scheduleRebuild();
    }

    /** Junta varias mudancas seguidas (ex.: arrastar um controle) em uma so atualizacao. */
    private void scheduleRebuild() {
        handler.removeCallbacks(rebuildTask);
        handler.postDelayed(rebuildTask, 40);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void unlock() {
        if (prefs != null) prefs.edit().putBoolean(Prefs.LOCK, false).apply();
    }

    private void removeSafe(View v) {
        if (wm == null || v == null) return;
        try {
            wm.removeView(v);
        } catch (Exception ignored) {
        }
    }

    private void clearZones() {
        for (View v : zoneViews) removeSafe(v);
        zoneViews.clear();
    }

    private void removeGap() {
        removeSafe(gapView);
        gapView = null;
    }

    private void removeLock() {
        if (lockView != null) {
            lockView.removeCallbacks(autoUnlock);
            removeSafe(lockView);
            lockView = null;
        }
    }

    private void rebuild() {
        handler.removeCallbacks(rebuildTask);
        if (wm == null || prefs == null) return;

        boolean locked = prefs.getBoolean(Prefs.LOCK, false) && !calibrating;
        if (locked) {
            if (lockView == null) addLock();
        } else {
            removeLock();
        }

        if (calibrating || locked || prefs.getBoolean(Prefs.EDITING, false)) {
            clearZones();
            removeGap();
            return;
        }

        Point size = Geo.screenSize(wm);
        boolean includeCustom = !prefs.getBoolean(Prefs.APP_OPEN, false);
        Zones.Layout layout = Zones.compute(this, size.x, size.y, Geo.rotation(this), includeCustom);
        boolean show = prefs.getBoolean(Prefs.SHOW, false);
        int color = show ? 0x55FF3B30 : Color.TRANSPARENT;

        // Reaproveita as janelas que ja existem (evita piscar ao arrastar os controles).
        while (zoneViews.size() > layout.blocked.size()) {
            removeSafe(zoneViews.remove(zoneViews.size() - 1));
        }
        for (int i = 0; i < layout.blocked.size(); i++) {
            Rect r = layout.blocked.get(i);
            WindowManager.LayoutParams lp = params(r.left, r.top, r.width(), r.height(), true);
            if (i < zoneViews.size()) {
                View v = zoneViews.get(i);
                v.setBackgroundColor(color);
                try {
                    wm.updateViewLayout(v, lp);
                } catch (Exception ignored) {
                }
            } else {
                View v = new View(this);
                v.setBackgroundColor(color);
                v.setOnTouchListener((view, ev) -> true);
                try {
                    wm.addView(v, lp);
                    zoneViews.add(v);
                } catch (Exception ignored) {
                }
            }
        }

        // Passagem do Painel Edge: so aparece (em verde) com "Mostrar zonas" ligado.
        removeGap();
        Rect g = layout.edgeGap;
        if (show && g != null) {
            View v = new View(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(0x3334C759);
            gd.setStroke(dp(2), 0xFF34C759);
            v.setBackground(gd);
            try {
                wm.addView(v, params(g.left, g.top, g.width(), g.height(), false));
                gapView = v;
            } catch (Exception ignored) {
            }
        }
    }

    private WindowManager.LayoutParams params(int x, int y, int w, int h, boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = x;
        lp.y = y;
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.setFitInsetsTypes(0);
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return lp;
    }

    /** Trava a tela toda. Para liberar, segure o botao por 2 segundos. */
    private void addLock() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setOnTouchListener((v, e) -> true);

        TextView btn = new TextView(this);
        btn.setText("🔒  Segure 2 s para liberar");
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(15);
        btn.setPadding(dp(22), dp(14), dp(22), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC1A1D24);
        bg.setCornerRadius(dp(28));
        btn.setBackground(bg);
        btn.setAlpha(0.55f);
        btn.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setAlpha(1f);
                    v.postDelayed(autoUnlock, HOLD_MS);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(0.55f);
                    v.removeCallbacks(autoUnlock);
                    break;
                default:
                    break;
            }
            return true;
        });

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        blp.bottomMargin = dp(110);
        root.addView(btn, blp);

        try {
            wm.addView(root, params(0, 0, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT, true));
            lockView = root;
        } catch (Exception e) {
            unlock();
        }
    }

    // ---------- Calibracao ----------

    /** Abre a tela de calibracao por cima de tudo. Devolve false se nao conseguiu. */
    boolean startCalibration(int seconds) {
        if (wm == null || calView != null) return false;
        calibrating = true;
        if (prefs.getBoolean(Prefs.LOCK, false)) prefs.edit().putBoolean(Prefs.LOCK, false).apply();
        rebuild();
        CalibrationView v = new CalibrationView(this, seconds * 1000L, this::onCalibrationDone);
        try {
            wm.addView(v, params(0, 0, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT, true));
            calView = v;
            return true;
        } catch (Exception e) {
            calibrating = false;
            rebuild();
            return false;
        }
    }

    private void onCalibrationDone(List<Prefs.Sample> samples) {
        // Sai da chamada atual antes de remover a janela da calibracao.
        handler.post(() -> {
            removeSafe(calView);
            calView = null;
            calibrating = false;
            if (samples != null) {
                Prefs.saveSamples(this, samples);
                Intent i = new Intent(this, ZoneEditorActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(ZoneEditorActivity.EXTRA_FROM_CAL, true);
                try {
                    startActivity(i);
                } catch (Exception ignored) {
                }
            }
            rebuild();
        });
    }
}
