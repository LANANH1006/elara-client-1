package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.UUID;

/**
 * Viền sáng quanh thân: ba lớp vỏ lăng trụ 10 cạnh bao quanh người chơi, lớp ngoài mờ hơn.
 * Đây là hào quang mờ, KHÔNG phải viền outline theo hình dáng model. Vẽ bằng pha cộng
 * nên các lớp chồng lên nhau sẽ sáng dần.
 */
public final class BodyGlow {
    private static final int SIDES = 10;
    private static final double BODY_R = 0.30;
    private static final double BODY_H = 1.85;

    private BodyGlow() {}

    public static void draw(WorldBatch b, MinecraftClient client, ElaraConfig.Layout l, int argb, float a) {
        double thick = Math.max(2, Math.min(40, l.get("thick", 10))) / 100.0;
        double size = Math.max(0.5, l.scalePct / 100.0);
        float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 1000.0 * 2.5);

        for (Map.Entry<UUID, PlayerTracker.Track> e : PlayerTracker.tracks().entrySet()) {
            PlayerTracker.Track tr = e.getValue();
            double px = PlayerTracker.lerp(tr.px, tr.x, a);
            double py = PlayerTracker.lerp(tr.py, tr.y, a);
            double pz = PlayerTracker.lerp(tr.pz, tr.z, a);
            if (!Visibility.allows(l, client, e.getKey(), px, py, pz)) continue;

            for (int k = 1; k <= 3; k++) {
                double r = (BODY_R + thick * k / 3.0) * size;
                int col = WorldBatch.withAlpha(argb, pulse * (0.55f - 0.15f * k));
                prism(b, px, py, pz, r, BODY_H * size, col);
            }
        }
    }

    /** Vỏ lăng trụ không đáy, chỉ vẽ các mặt bên */
    private static void prism(WorldBatch b, double cx, double cy, double cz, double r, double h, int col) {
        WorldBatch.V[] lo = new WorldBatch.V[SIDES];
        WorldBatch.V[] hi = new WorldBatch.V[SIDES];
        for (int i = 0; i < SIDES; i++) {
            double ang = 2 * Math.PI * i / SIDES;
            double x = cx + r * Math.cos(ang);
            double z = cz + r * Math.sin(ang);
            lo[i] = new WorldBatch.V(x, cy, z);
            hi[i] = new WorldBatch.V(x, cy + h, z);
        }
        for (int i = 0; i < SIDES; i++) {
            int j = (i + 1) % SIDES;
            b.quad(lo[i], lo[j], hi[j], hi[i], col);
        }
    }
}
