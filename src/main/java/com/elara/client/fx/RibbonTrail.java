package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.UUID;

/**
 * Dải lụa sau lưng: nối các điểm vết di chuyển (PlayerMotion.trail) thành một dải luôn quay mặt về
 * camera. Càng xa đầu vệt thì càng hẹp và càng mờ.
 * Đứng yên thì các điểm trùng nhau nên không có dải nào hiện ra.
 */
public final class RibbonTrail {
    /** Độ cao dải so với chân, gần ngang lưng */
    private static final double HEIGHT = 1.0;

    private RibbonTrail() {}

    public static void draw(WorldBatch b, MinecraftClient client, ElaraConfig.Layout l, int argb, float a,
                            WorldBatch.V cam) {
        int len = Math.max(4, Math.min(PlayerMotion.TRAIL_LEN, l.get("len", 16)));
        double size = Math.max(0.5, l.scalePct / 100.0);

        for (Map.Entry<UUID, PlayerTracker.Track> e : PlayerTracker.tracks().entrySet()) {
            PlayerMotion.State m = PlayerMotion.get(e.getKey());
            if (m == null || m.trail.size() < 2) continue;
            PlayerTracker.Track tr = e.getValue();
            double px = PlayerTracker.lerp(tr.px, tr.x, a);
            double py = PlayerTracker.lerp(tr.py, tr.y, a);
            double pz = PlayerTracker.lerp(tr.pz, tr.z, a);
            if (!Visibility.allows(l, client, e.getKey(), px, py, pz)) continue;

            // Các điểm lấy từ mới nhất đến cũ nhất
            double[][] pts = m.trail.toArray(new double[0][]);
            int n = Math.min(len, pts.length);
            for (int i = 0; i < n - 1; i++) {
                float k = 1f - i / (float) n;
                WorldBatch.V p0 = new WorldBatch.V(pts[i][0], pts[i][1] + HEIGHT * size, pts[i][2]);
                WorldBatch.V p1 = new WorldBatch.V(pts[i + 1][0], pts[i + 1][1] + HEIGHT * size, pts[i + 1][2]);
                b.billboardBeam(p0, p1, cam, (0.05 + 0.2 * k) * size, WorldBatch.withAlpha(argb, 0.85f * k));
            }
        }
    }
}
