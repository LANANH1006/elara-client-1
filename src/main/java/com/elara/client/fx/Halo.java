package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.music.MusicPlayer;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.UUID;

/**
 * Phụ kiện đầu. Kiểu 0: vòng hào quang đơn giản, xoay chậm.
 * Kiểu 1: sóng nhạc, gồm 36 cột sáng đứng trên vòng. Độ cao các cột chạy vòng quanh như một đợt sóng,
 * bên ngoài có dải lụa xoắn hai vòng quay chậm. Sóng chạy theo thời gian, không đọc âm lượng nhạc.
 */
public final class Halo {
    public static final int STYLES = 2;
    private static final int BARS = 36;
    private static final int RIBBON = 64;

    private Halo() {}

    public static void draw(WorldBatch b, MinecraftClient client, ElaraConfig.Layout l, int argb, float a,
                            WorldBatch.V cam) {
        int style = Math.max(0, Math.min(STYLES - 1, l.get("style", 0)));
        double size = Math.max(0.5, l.scalePct / 100.0);
        double t = System.currentTimeMillis() / 1000.0;

        for (Map.Entry<UUID, PlayerTracker.Track> e : PlayerTracker.tracks().entrySet()) {
            PlayerTracker.Track tr = e.getValue();
            double px = PlayerTracker.lerp(tr.px, tr.x, a);
            double py = PlayerTracker.lerp(tr.py, tr.y, a);
            double pz = PlayerTracker.lerp(tr.pz, tr.z, a);
            if (!Visibility.allows(l, client, e.getKey(), px, py, pz)) continue;

            WorldBatch.V c = new WorldBatch.V(px, py + 2.15, pz);
            double r = 0.28 * size;
            if (style == 1) {
                wave(b, c, r, size, t, argb, cam);
            } else {
                b.ring(c, r, 0.035, 40, t * 1.2, argb);
                b.ring(c, r, 0.12, 40, t * 1.2, WorldBatch.withAlpha(argb, 0.25f));
            }
        }
    }

    private static void wave(WorldBatch b, WorldBatch.V c, double r, double size, double t, int argb,
                             WorldBatch.V cam) {
        b.ring(c, r, 0.025, 40, 0, WorldBatch.withAlpha(argb, 0.7f));

        // Dữ liệu tần số thật của nhạc đang phát (8 dải, 0..1). Không phát nhạc thì các dải bằng 0.
        float[] bands = MusicPlayer.bands();

        // Cột sóng: độ cao chạy vòng quanh theo góc, cộng thêm biên độ của dải tần tương ứng
        for (int i = 0; i < BARS; i++) {
            double ang = 2 * Math.PI * i / BARS;
            double band = bands[i * bands.length / BARS];
            double h = (0.03 + 0.10 * (0.5 + 0.5 * Math.sin(t * 6.0 - i * 0.55)) + 0.22 * band) * size;
            WorldBatch.V base = new WorldBatch.V(c.x() + r * Math.cos(ang), c.y(), c.z() + r * Math.sin(ang));
            b.billboardBeam(base, base.add(new WorldBatch.V(0, h, 0)), cam, 0.035 * size, argb);
        }

        // Dải lụa xoắn: hai vòng, bán kính lớn hơn vòng, cao dần rồi quay theo thời gian
        WorldBatch.V prev = null;
        for (int k = 0; k <= RIBBON; k++) {
            double u = k / (double) RIBBON;
            double ang = 4 * Math.PI * u + t * 1.4;
            double rr = r * 1.18;
            double y = c.y() - 0.07 * size + 0.14 * size * u;
            WorldBatch.V cur = new WorldBatch.V(c.x() + rr * Math.cos(ang), y, c.z() + rr * Math.sin(ang));
            if (prev != null) {
                float alpha = 0.35f + 0.5f * (float) Math.sin(Math.PI * u);
                b.billboardBeam(prev, cur, cam, 0.02 * size, WorldBatch.withAlpha(argb, alpha));
            }
            prev = cur;
        }
    }
}
