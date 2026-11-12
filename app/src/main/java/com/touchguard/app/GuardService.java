package com.touchguard.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;

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

    /** Indica se o servico esta ligado neste momento (usado pelo app e pelo botao rapido). */
    static volatile boolean running = false;

    private WindowManager wm;
    private SharedPreferences prefs;
    private final List<View> overlays = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoUnlock = this::unlock;

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = Prefs.get(this);
        // Nunca comecar travado (ex.: depois de reiniciar o celular com a trava ligada).
        prefs.edit().putBoolean(Prefs.LOCK, false).commit();
        prefs.registerOnSharedPreferenceChangeListener(this);
        running = true;
        rebuild();
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
        running = false;
        handler.removeCallbacks(autoUnlock);
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            prefs.edit().putBoolean(Prefs.LOCK, false).apply();
        }
        clear();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Recalcula as faixas ao girar a tela.
        if (wm != null) rebuild();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        rebuild();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int screenHeight() {
        if (Build.VERSION.SDK_INT >= 30) {
            return wm.getCurrentWindowMetrics().getBounds().height();
        }
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        return m.heightPixels;
    }

    private void clear() {
        if (wm == null) return;
        for (View v : overlays) {
            try {
                wm.removeView(v);
            } catch (Exception ignored) {
            }
        }
        overlays.clear();
    }

    private void unlock() {
        if (prefs != null) prefs.edit().putBoolean(Prefs.LOCK, false).apply();
    }

    private void rebuild() {
        if (wm == null || prefs == null) return;
        clear();
        handler.removeCallbacks(autoUnlock);

        if (prefs.getBoolean(Prefs.LOCK, false)) {
            addLock();
            int min = Prefs.autoUnlock(this);
            if (min > 0) handler.postDelayed(autoUnlock, min * 60_000L);
            return;
        }

        boolean show = prefs.getBoolean(Prefs.SHOW, false);
        int left = dp(Prefs.zone(this, Prefs.LEFT));
        int right = dp(Prefs.zone(this, Prefs.RIGHT));
        int top = dp(Prefs.zone(this, Prefs.TOP));
        int bottom = dp(Prefs.zone(this, Prefs.BOTTOM));
        int match = WindowManager.LayoutParams.MATCH_PARENT;

        // Faixas laterais: opcionalmente deixam livres a barra de status e a barra de gestos.
        int sideY = 0;
        int sideH = match;
        if (Prefs.spareBars(this)) {
            int gap = dp(Prefs.BAR_GAP_DP);
            sideY = gap;
            sideH = Math.max(screenHeight() - 2 * gap, dp(48));
        }

        if (left > 0) addZone(Gravity.START | Gravity.TOP, left, sideH, sideY, show);
        if (right > 0) addZone(Gravity.END | Gravity.TOP, right, sideH, sideY, show);
        if (top > 0) addZone(Gravity.TOP, match, top, 0, show);
        if (bottom > 0) addZone(Gravity.BOTTOM, match, bottom, 0, show);
    }

    private WindowManager.LayoutParams params(int gravity, int w, int h, int y) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        lp.y = y;
        return lp;
    }

    private void addZone(int gravity, int w, int h, int y, boolean show) {
        View v = new View(this);
        v.setBackgroundColor(show ? 0x55FF0000 : Color.TRANSPARENT);
        v.setOnTouchListener((view, ev) -> true);
        try {
            wm.addView(v, params(gravity, w, h, y));
            overlays.add(v);
        } catch (Exception ignored) {
        }
    }

    /** Trava a tela toda. Para liberar, segure o botao por 2 segundos. */
    private void addLock() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setOnTouchListener((v, e) -> true);

        Button btn = new Button(this);
        btn.setText("Segure 2s para liberar");
        btn.setAllCaps(false);
        btn.setAlpha(0.6f);

        btn.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setAlpha(1f);
                    v.postDelayed(autoUnlock, HOLD_MS);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(0.6f);
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
        blp.bottomMargin = dp(96);
        root.addView(btn, blp);

        try {
            wm.addView(root, params(Gravity.TOP,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT, 0));
            overlays.add(root);
        } catch (Exception e) {
            unlock();
        }
    }
}
