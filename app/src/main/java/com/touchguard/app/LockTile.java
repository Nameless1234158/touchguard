package com.touchguard.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Botao no painel rapido para travar/destravar a tela toda. */
public class LockTile extends TileService {

    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        if (!GuardService.running) {
            // Servico desligado: a trava nao funcionaria. Abre o app para o usuario ativar.
            openApp();
            return;
        }
        SharedPreferences p = Prefs.get(this);
        p.edit().putBoolean(Prefs.LOCK, !p.getBoolean(Prefs.LOCK, false)).apply();
        refresh();
    }

    private void openApp() {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapseLegacy(i);
        }
    }

    @SuppressWarnings("deprecation")
    private void startActivityAndCollapseLegacy(Intent i) {
        startActivityAndCollapse(i);
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = GuardService.running && Prefs.get(this).getBoolean(Prefs.LOCK, false);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(getString(R.string.tile_label));
        if (Build.VERSION.SDK_INT >= 29) {
            t.setSubtitle(GuardService.running ? (on ? "Travado" : "Livre") : "Serviço desligado");
        }
        t.updateTile();
    }
}
