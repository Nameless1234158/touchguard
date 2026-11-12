package com.touchguard.app;

import android.graphics.RectF;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Transforma os toques registrados na calibracao em zonas sugeridas: divide a tela
 * numa grade, marca as celulas que receberam toques e junta celulas vizinhas
 * em um unico retangulo, com uma pequena margem.
 */
final class Suggest {
    static final int GX = 12;
    static final int GY = 24;

    private Suggest() {}

    static List<RectF> fromSamples(List<Prefs.Sample> samples) {
        List<RectF> out = new ArrayList<>();
        if (samples == null || samples.isEmpty()) return out;

        int[][] count = new int[GX][GY];
        for (Prefs.Sample s : samples) {
            int cx = Prefs.clamp((int) (s.x * GX), 0, GX - 1);
            int cy = Prefs.clamp((int) (s.y * GY), 0, GY - 1);
            count[cx][cy]++;
        }

        boolean[][] seen = new boolean[GX][GY];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < GX; x++) {
            for (int y = 0; y < GY; y++) {
                if (count[x][y] == 0 || seen[x][y]) continue;
                int minX = x, maxX = x, minY = y, maxY = y;
                seen[x][y] = true;
                queue.add(new int[]{x, y});
                while (!queue.isEmpty()) {
                    int[] p = queue.poll();
                    minX = Math.min(minX, p[0]);
                    maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxY = Math.max(maxY, p[1]);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            int nx = p[0] + dx, ny = p[1] + dy;
                            if (nx < 0 || ny < 0 || nx >= GX || ny >= GY) continue;
                            if (seen[nx][ny] || count[nx][ny] == 0) continue;
                            seen[nx][ny] = true;
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }
                float l = clamp01((minX - 0.5f) / GX);
                float t = clamp01((minY - 0.5f) / GY);
                float r = clamp01((maxX + 1.5f) / GX);
                float b = clamp01((maxY + 1.5f) / GY);
                out.add(new RectF(l, t, r, b));
            }
        }
        return out;
    }

    /** Quantos toques caem dentro de um retangulo (para mostrar no editor). */
    static int countIn(List<Prefs.Sample> samples, RectF r) {
        int n = 0;
        for (Prefs.Sample s : samples) if (r.contains(s.x, s.y)) n++;
        return n;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
