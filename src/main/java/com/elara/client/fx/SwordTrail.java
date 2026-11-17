package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import net.minecraft.client.MinecraftClient;

/**
 * Vệt chém kiếm: một dải cong trước mặt, lưỡi quét qua trong khoảng 13 khung
 * (khoảng 650 ms), phần đuôi mờ dần. Đây là dải vẽ quanh người chém, không dính vào tay.
 */
public final class SwordTrail {
    private static final int SEGS = 12;
    private static final long DURATION_MS = 650L;
    private static final double ARC = Math.toRadians(120);
    private static final double RADIUS = 1.4;

    private SwordTrail() {}

    public static void draw(WorldBatch b, MinecraftClient client, ElaraConfig.Layout l, int argb) {
        long now = System.currentTimeMillis();
        for (PlayerTracker.Slash s : PlayerTracker.slashes()) {
            long age = now - s.startMs();
            if (age < 0 || age >= DURATION_MS) continue;
            if (!Visibility.allows(l, client, s.owner(), s.x(), s.y(), s.z())) continue;

            float t = age / (float) DURATION_MS;
            // Nhanh ở đầu, chậm dần về cuối
            double ease = 1 - (1 - t) * (1 - t);
            double yaw = Math.toRadians(s.yaw());
            double start = -ARC / 2;
            double head = start + ARC * ease;
            WorldBatch.V o = new WorldBatch.V(s.x(), s.y(), s.z());

            for (int i = 0; i < SEGS; i++) {
                double th0 = start + ARC * i / SEGS;
                double th1 = Math.min(start + ARC * (i + 1) / SEGS, head);
                if (th0 >= head) break;
                // 0 ở lưỡi kiếm, tăng dần về phía đuôi vệt
                double fromHead = (head - th0) / ARC;
                float k = (float) ((1 - fromHead) * (1 - t));
                b.quad(point(o, yaw + th0, 0.7), point(o, yaw + th1, 0.7),
                        point(o, yaw + th1, 1.3), point(o, yaw + th0, 1.3),
                        WorldBatch.withAlpha(argb, k));
            }
        }
    }

    /** Điểm trên cung tròn bán kính RADIUS quanh o, theo góc ang (quy ước của Minecraft) */
    private static WorldBatch.V point(WorldBatch.V o, double ang, double h) {
        return new WorldBatch.V(o.x() - Math.sin(ang) * RADIUS, o.y() + h, o.z() + Math.cos(ang) * RADIUS);
    }
}
