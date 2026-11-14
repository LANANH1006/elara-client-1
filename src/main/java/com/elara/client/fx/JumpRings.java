package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import net.minecraft.client.MinecraftClient;

/**
 * Vòng dưới chân khi nhảy, nở ra rồi mờ dần.
 * Kiểu: 0 = sóng đơn, 1 = vòng xoay đôi, 2 = lục giác ma thuật (có ngôi sao).
 */
public final class JumpRings {
    private static final int SEGS = 48;

    private JumpRings() {}

    public static void draw(WorldBatch b, MinecraftClient client, ElaraConfig.Layout l, int argb) {
        int style = l.get("style", 0);
        int speed = Math.max(1, Math.min(10, l.get("speed", 5)));
        long duration = 1600L - speed * 110L;
        long now = System.currentTimeMillis();
        for (PlayerTracker.Jump j : PlayerTracker.jumps()) {
            long age = now - j.startMs();
            if (age < 0 || age >= duration) continue;
            if (!Visibility.allows(l, client, j.owner(), j.x(), j.y(), j.z())) continue;

            float t = age / (float) duration;
            float fade = 1f - t;
            int alpha = (int) (((argb >>> 24) & 0xFF) * fade * fade);
            int col = (alpha << 24) | (argb & 0xFFFFFF);
            WorldBatch.V c = new WorldBatch.V(j.x(), j.y() + 0.03, j.z());
            switch (style) {
                case 1 -> doubleRing(b, c, t, col);
                case 2 -> magic(b, c, t, col);
                default -> wave(b, c, t, col);
            }
        }
    }

    private static void wave(WorldBatch b, WorldBatch.V c, float t, int col) {
        ring(b, c, 0.25 + t * 1.8, 0.08, SEGS, 0, col, false);
    }

    private static void doubleRing(WorldBatch b, WorldBatch.V c, float t, int col) {
        double rot = t * Math.PI * 4;
        ring(b, c, 0.2 + t * 1.3, 0.07, 24, rot, col, true);
        ring(b, c, 0.2 + t * 0.9, 0.07, 18, -rot, col, true);
    }

    private static void magic(WorldBatch b, WorldBatch.V c, float t, int col) {
        double r = 0.25 + t * 1.2;
        double rot = t * Math.PI;
        WorldBatch.V[] hex = new WorldBatch.V[6];
        for (int i = 0; i < 6; i++) {
            hex[i] = polar(c, r, rot + Math.PI / 3 * i);
        }
        for (int i = 0; i < 6; i++) {
            b.flatLine(hex[i], hex[(i + 1) % 6], 0.05, col);
        }
        // Nối đỉnh cách nhau một: tạo hai tam giác đều chồng lên nhau thành ngôi sao
        for (int i = 0; i < 6; i++) {
            b.flatLine(hex[i], hex[(i + 2) % 6], 0.035, col);
        }
        ring(b, c, r * 0.5, 0.04, 36, -rot, col, false);
    }

    private static void ring(WorldBatch b, WorldBatch.V c, double r, double thick, int segs,
                             double rot, int col, boolean dashed) {
        double ri = r - thick / 2;
        double ro = r + thick / 2;
        for (int i = 0; i < segs; i++) {
            if (dashed && i % 2 == 1) continue;
            double a0 = rot + 2 * Math.PI * i / segs;
            double a1 = rot + 2 * Math.PI * (i + 1) / segs;
            b.quad(polar(c, ri, a0), polar(c, ro, a0), polar(c, ro, a1), polar(c, ri, a1), col);
        }
    }

    private static WorldBatch.V polar(WorldBatch.V c, double r, double a) {
        return new WorldBatch.V(c.x() + r * Math.cos(a), c.y(), c.z() + r * Math.sin(a));
    }
}
