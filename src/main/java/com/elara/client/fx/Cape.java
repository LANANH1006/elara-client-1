package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.UUID;

/**
 * Áo choàng 3D: một tấm vải treo từ vai, lưới 6 hàng x 4 cột. Độ nghiêng (PlayerMotion.lean)
 * đẩy vạt ra sau khi chạy, nhảy thì vạt rung mạnh hơn. Thân vải lấy từ bảng màu của kiểu, còn viền
 * vạt dùng màu đã chọn trong bảng HSB, nên đổi màu và chroma vẫn có tác dụng.
 * Vải chỉ vẽ bằng màu, chưa dùng texture PNG.
 */
public final class Cape {
    public static final int STYLES = 3;
    private static final int ROWS = 6;
    private static final int COLS = 4;
    private static final double WIDTH = 0.52;
    private static final double LENGTH = 0.95;
    private static final double SHOULDER_Y = 1.36;

    /** Màu đầu vạt (trên) và cuối vạt (dưới) của từng kiểu, ARGB: 0 Sakura, 1 Kính lỏng, 2 Neon tím */
    private static final int[][] PALETTE = {
            {0xEEFFC4DD, 0xEE9E2D5E},
            {0xB8E6F4FF, 0x8C5C7FA8},
            {0xEEB48CFF, 0xEE2A1656},
    };

    private Cape() {}

    public static void draw(WorldBatch b, MinecraftClient client, ElaraConfig.Layout l, int accent, float a) {
        int style = Math.max(0, Math.min(STYLES - 1, l.get("style", 0)));
        double size = Math.max(0.5, l.scalePct / 100.0);
        double t = System.currentTimeMillis() / 1000.0;
        int top = PALETTE[style][0];
        int bottom = PALETTE[style][1];

        for (Map.Entry<UUID, PlayerTracker.Track> e : PlayerTracker.tracks().entrySet()) {
            PlayerTracker.Track tr = e.getValue();
            double px = PlayerTracker.lerp(tr.px, tr.x, a);
            double py = PlayerTracker.lerp(tr.py, tr.y, a);
            double pz = PlayerTracker.lerp(tr.pz, tr.z, a);
            if (!Visibility.allows(l, client, e.getKey(), px, py, pz)) continue;
            PlayerMotion.State m = PlayerMotion.get(e.getKey());
            if (m == null) continue;

            // Quy ước của Minecraft: phải = (-cos, -sin), sau = (sin, -cos)
            double yr = Math.toRadians(tr.yaw);
            WorldBatch.V right = new WorldBatch.V(-Math.cos(yr), 0, -Math.sin(yr));
            WorldBatch.V back = new WorldBatch.V(Math.sin(yr), 0, -Math.cos(yr));
            WorldBatch.V shoulder = new WorldBatch.V(px, py + SHOULDER_Y, pz).add(back.mul(0.13 * size));

            float lean = m.lean;
            double gust = m.airborne ? (m.vy > 0 ? 0.04 : 0.10) : 0.0;
            double speed = 2.6 + 3.0 * lean;

            WorldBatch.V[][] p = new WorldBatch.V[ROWS + 1][COLS + 1];
            for (int r = 0; r <= ROWS; r++) {
                double f = r / (double) ROWS;
                double drop = LENGTH * size * f * (1 - 0.25 * lean);
                double wave = size * (0.03 + gust) * f * Math.sin(t * speed + f * 3.0);
                double backOff = size * (0.06 + 0.5 * lean) * Math.pow(f, 1.4) + wave;
                for (int c = 0; c <= COLS; c++) {
                    double u = c / (double) COLS - 0.5;
                    double lateral = u * WIDTH * size * (1 + 0.15 * f);
                    double bulge = 0.035 * size * (1 - 4 * u * u) * f;
                    p[r][c] = shoulder.add(right.mul(lateral))
                            .add(new WorldBatch.V(0, -drop, 0))
                            .add(back.mul(backOff + bulge));
                }
            }
            for (int r = 0; r < ROWS; r++) {
                int col = lerpArgb(top, bottom, r / (float) ROWS);
                for (int c = 0; c < COLS; c++) {
                    b.quad(p[r][c], p[r][c + 1], p[r + 1][c + 1], p[r + 1][c], col);
                }
            }
            // Viền vạt: màu đã chọn
            for (int c = 0; c < COLS; c++) {
                b.beam(p[ROWS][c], p[ROWS][c + 1], 0.035 * size, accent);
            }
        }
    }

    private static int lerpArgb(int c1, int c2, float t) {
        int alpha = mix((c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF, t);
        int red = mix((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, t);
        int green = mix((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, t);
        int blue = mix(c1 & 0xFF, c2 & 0xFF, t);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int mix(int x, int y, float t) {
        return Math.round(x + (y - x) * t);
    }
}
