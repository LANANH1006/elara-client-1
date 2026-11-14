package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import net.minecraft.client.MinecraftClient;

/**
 * Hào quang bước chân. Kiểu 0: vòng nở ra dưới chân. Kiểu 1: đom đóm bay quanh chân.
 */
public final class Footsteps {
    private static final long DURATION_MS = 900L;
    private static final double S = 0.035;

    private Footsteps() {}

    public static void draw(WorldBatch b, MinecraftClient client, ElaraConfig.Layout l, int argb) {
        int style = l.get("style", 0);
        long now = System.currentTimeMillis();
        for (PlayerTracker.Step s : PlayerTracker.steps()) {
            long age = now - s.startMs();
            if (age < 0 || age >= DURATION_MS) continue;
            if (!Visibility.allows(l, client, s.owner(), s.x(), s.y(), s.z())) continue;

            float t = age / (float) DURATION_MS;
            float fade = 1f - t;
            WorldBatch.V c = new WorldBatch.V(s.x(), s.y() + 0.03, s.z());
            if (style == 1) {
                fireflies(b, c, t, WorldBatch.withAlpha(argb, fade));
            } else {
                b.ring(c, 0.15 + t * 0.6, 0.05, 28, 0, WorldBatch.withAlpha(argb, fade * fade));
            }
        }
    }

    /** Ba con đom đóm quay quanh chân, mỗi con là hai tấm vuông vuông góc để nhìn từ mọi hướng */
    private static void fireflies(WorldBatch b, WorldBatch.V c, float t, int col) {
        for (int k = 0; k < 3; k++) {
            double ang = t * Math.PI * 3 + k * 2 * Math.PI / 3;
            double r = 0.35 + 0.1 * Math.sin(t * 6 + k);
            double h = 0.25 + 0.2 * Math.sin(t * 8 + k * 1.7);
            WorldBatch.V p = new WorldBatch.V(c.x() + r * Math.cos(ang), c.y() + h, c.z() + r * Math.sin(ang));
            b.quad(p.add(new WorldBatch.V(-S, -S, 0)), p.add(new WorldBatch.V(S, -S, 0)),
                    p.add(new WorldBatch.V(S, S, 0)), p.add(new WorldBatch.V(-S, S, 0)), col);
            b.quad(p.add(new WorldBatch.V(0, -S, -S)), p.add(new WorldBatch.V(0, -S, S)),
                    p.add(new WorldBatch.V(0, S, S)), p.add(new WorldBatch.V(0, S, -S)), col);
        }
    }
}
