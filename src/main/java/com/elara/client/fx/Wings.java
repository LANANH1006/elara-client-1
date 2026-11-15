package com.elara.client.fx;

import com.elara.client.core.ElaraAssets;
import com.elara.client.core.ElaraConfig;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;

/**
 * Cánh ảo nhiều lớp lông vũ, vẽ bằng texture lông vũ thật (wings_feather.png, RGBA 256x256).
 * Khung xương có 4 khớp (vai, khuỷu, cổ tay, chóp), tức 3 đốt. Mỗi khớp vỗ lệch pha so với khớp trước,
 * biên độ tăng dần ra đầu cánh. Cánh vòm lên ở giữa và vuốt nhọn ra sau ở chóp, kiểu seraphim.
 * Kiểu: 0 thiên thần, 1 rồng (mép răng cưa), 2 bướm (một lớp, có đốm), 3 cyber (khung dây sáng),
 * 4 quạ đen (lông đen, khói bay lên). Trạng thái vỗ, xòe, thu, ẩn lấy từ PlayerMotion.
 */
public final class Wings {
    public static final int STYLES = 5;
    private static final int SEC = 4;
    /** Vị trí các khớp dọc theo cánh: 0 vai, 1 khuỷu, 2 cổ tay, 3 chóp */
    private static final double[] JOINT_U = {0.0, 0.36, 0.70, 1.0};
    /** Mỗi bên khoảng 1.45 block, sải cánh khoảng 2.9 block khi xòe */
    private static final double ARM_LEN = 1.45;
    /** Gốc cánh ở độ cao vai, tính từ chân người chơi */
    private static final double ROOT_Y = 1.40;
    /** Gốc cánh lùi ra sau thân để không xuyên qua áo choàng */
    private static final double ROOT_BACK = 0.18;
    private static final WorldBatch.V UP = new WorldBatch.V(0, 1, 0);

    /** Cách một kiểu cánh trông thế nào */
    private record Look(int base, int tip, double wMax, double lenK, boolean spiky, boolean wire,
                        boolean smoke, boolean spots, boolean single) {}

    private Wings() {}

    /** Vẽ cánh cho mọi người chơi đang theo dõi. Tự flush cả hai batch. */
    public static void draw(Matrix4f mat, MinecraftClient client, ElaraConfig.Layout l, int argb, float a,
                            WorldBatch.V cam) {
        ElaraAssets.ensureLoaded();
        int style = clamp(l.get("style", 0), 0, STYLES - 1);
        double size = Math.max(0.5, l.scalePct / 100.0);
        int speed = clamp(l.get("speed", 5), 1, 10);
        double t = System.currentTimeMillis() / 1000.0;
        Look look = look(style, argb);
        WorldBatch wb = new WorldBatch(mat, cam.x(), cam.y(), cam.z(), true);
        TexBatch tb = new TexBatch(mat, cam.x(), cam.y(), cam.z(), ElaraAssets.WING_FEATHER);

        for (Map.Entry<UUID, PlayerTracker.Track> e : PlayerTracker.tracks().entrySet()) {
            PlayerTracker.Track tr = e.getValue();
            double px = PlayerTracker.lerp(tr.px, tr.x, a);
            double py = PlayerTracker.lerp(tr.py, tr.y, a);
            double pz = PlayerTracker.lerp(tr.pz, tr.z, a);
            if (!Visibility.allows(l, client, e.getKey(), px, py, pz)) continue;
            PlayerMotion.State m = PlayerMotion.get(e.getKey());
            if (m == null || m.hidden) continue;

            double yr = Math.toRadians(tr.yaw);
            // Quy ước của Minecraft: phải = (-cos, -sin), sau = (sin, -cos)
            WorldBatch.V right = new WorldBatch.V(-Math.cos(yr), 0, -Math.sin(yr));
            WorldBatch.V back = new WorldBatch.V(Math.sin(yr), 0, -Math.cos(yr));
            WorldBatch.V root = new WorldBatch.V(px, py + ROOT_Y, pz).add(back.mul(ROOT_BACK));

            // Vỗ: chậm khi đứng yên, nhịp nhanh hơn khi đi, tốc độ chỉnh được
            double freq = m.moving ? 2.0 + speed * 0.25 : 0.8;
            double phase = t * Math.PI * 2 * freq;
            for (int side = -1; side <= 1; side += 2) {
                drawWing(wb, tb, look, root, right.mul(side), back, size, m.spread, m.fold, m.flap,
                        phase, t, cam, style);
            }
        }
        wb.flush();
        tb.flush();
    }

    private static void drawWing(WorldBatch wb, TexBatch tb, Look look, WorldBatch.V root, WorldBatch.V span,
                                 WorldBatch.V back, double size, float spread, float fold, float amp,
                                 double phase, double t, WorldBatch.V cam, int style) {
        int n = JOINT_U.length;
        WorldBatch.V[] joint = new WorldBatch.V[n];
        for (int k = 0; k < n; k++) {
            double u = JOINT_U[k];
            double lateral = size * ARM_LEN * (0.35 + 0.65 * spread) * u;
            // Vòm lên ở giữa, chóp vuốt lên như cánh thiên thần
            double lift = size * (0.28 * spread * Math.sin(Math.PI * u * 0.8) + 0.45 * spread * u * u * u);
            // Mỗi khớp vỗ lệch pha so với khớp trước, biên độ tăng dần ra đầu cánh
            double wave = amp * Math.sin(phase - k * 0.9) * u * size * 0.9;
            // Chóp vuốt ra sau, và cánh thu thì sát người hơn
            double backOff = size * (0.10 * u * u + 0.22 * fold * u + 0.20 * spread * u * u);
            joint[k] = root.add(span.mul(lateral)).add(UP.mul(lift + wave)).add(back.mul(backOff));
        }

        if (look.single()) {
            // Bướm: một lớp lông tròn, dài
            for (int k = 1; k < n; k++) {
                WorldBatch.V dir = back.mul(-0.6).add(UP.mul(0.45)).add(span.mul(0.5));
                feather(wb, tb, look, joint[k], dir, size * look.lenK() * 0.8, look.wMax(), 0.02, k, cam, size);
            }
            return;
        }

        // Ba lớp: lông chính (đuôi cánh), lông phụ, lông che (sát cánh tay)
        double[][] layers = {
                {-1.0, -0.12, 0.22, 0.9},
                {-0.85, 0.22, 0.1, 0.65},
                {-0.55, 0.5, 0.0, 0.4},
        };
        for (double[] ly : layers) {
            for (int k = 1; k < n; k++) {
                // Lông dài dần ra đầu cánh
                double grow = 0.55 + 0.45 * JOINT_U[k];
                WorldBatch.V dir = back.mul(ly[0]).add(UP.mul(ly[1])).add(span.mul(ly[2]));
                double len = size * look.lenK() * ly[3] * grow;
                feather(wb, tb, look, joint[k], dir, len, look.wMax(), 0.06 * size, k, cam, size);
            }
        }

        if (look.smoke()) {
            // Khói đen bay lên từ chóp cánh, lặp theo thời gian
            WorldBatch.V tip = joint[n - 1];
            for (int i = 0; i < 2; i++) {
                double frac = (t * 0.35 + i * 0.5) % 1.0;
                WorldBatch.V bottom = tip.add(UP.mul(0.05 * size));
                WorldBatch.V top = tip.add(UP.mul(size * (0.25 + 0.6 * frac)));
                int col = WorldBatch.withAlpha(0xFF303038, (float) ((1 - frac) * 0.5));
                wb.billboardBeam(bottom, top, cam, 0.09 * size, col);
            }
        }
    }

    /**
     * Một lông: dải hình lá thon đầu, vẽ bằng texture lông vũ. Chiều rộng nằm ngang và vuông góc với
     * hướng lông, nên các lông xếp thành một mặt cánh phẳng, không gãy.
     * Kiểu cyber không dùng texture mà vẽ khung dây sáng.
     */
    private static void feather(WorldBatch wb, TexBatch tb, Look look, WorldBatch.V base, WorldBatch.V dirRaw,
                                double len, double wMax, double curl, int seed, WorldBatch.V cam, double size) {
        WorldBatch.V d = unit(dirRaw);
        if (d == null) return;
        WorldBatch.V wd = unit(cross(d, UP));
        if (wd == null) return;
        WorldBatch.V nrm = unit(cross(wd, d));
        if (nrm == null) nrm = UP;

        WorldBatch.V[] c = new WorldBatch.V[SEC + 1];
        double[] w = new double[SEC + 1];
        for (int j = 0; j <= SEC; j++) {
            double f = j / (double) SEC;
            c[j] = base.add(d.mul(len * f)).add(UP.mul(curl * f * f));
            double shape = Math.sin(Math.PI * Math.min(1.0, 0.05 + f * 0.9));
            w[j] = wMax * size * shape * (1 - 0.35 * f);
            if (look.spiky() && j % 2 == 1) w[j] *= 0.55;
        }

        double t = System.currentTimeMillis() / 1000.0;
        for (int j = 0; j < SEC; j++) {
            WorldBatch.V p0 = c[j];
            WorldBatch.V p1 = c[j + 1];
            if (look.wire()) {
                // Cyber: chỉ vẽ khung dây, sáng nhấp nháy
                double glow = 0.5 + 0.5 * Math.sin(t * 6 + j + seed);
                int wc = WorldBatch.withAlpha(look.base(), (float) (0.4 + 0.6 * glow));
                double lw = 0.012 * size;
                wb.beam(p0.add(wd.mul(w[j])), p1.add(wd.mul(w[j + 1])), lw, wc);
                wb.beam(p0.sub(wd.mul(w[j])), p1.sub(wd.mul(w[j + 1])), lw, wc);
                wb.beam(p0, p1, lw * 0.8, wc);
            } else {
                float v0 = j / (float) SEC;
                float v1 = (j + 1) / (float) SEC;
                int col = mix(look.base(), look.tip(), v0);
                // u = 0 ở mép trái, u = 1 ở mép phải của lông
                tb.quad(p0.sub(wd.mul(w[j])), p0.add(wd.mul(w[j])),
                        p1.add(wd.mul(w[j + 1])), p1.sub(wd.mul(w[j + 1])), v0, v1, col, nrm);
            }
        }

        if (look.spots()) {
            // Đốm trên cánh bướm
            WorldBatch.V mid = c[2];
            double r = 0.07 * size;
            int spot = WorldBatch.withAlpha(0xFFFFFFFF, 0.8f);
            wb.quad(mid.add(wd.mul(r)), mid.add(UP.mul(r)), mid.sub(wd.mul(r)), mid.sub(UP.mul(r)), spot);
        }
    }

    private static Look look(int style, int argb) {
        return switch (style) {
            case 1 -> new Look(darken(argb, 0.8f), WorldBatch.withAlpha(argb, 0.4f),
                    0.17, 1.2, true, false, false, false, false);
            case 2 -> new Look(WorldBatch.withAlpha(argb, 0.7f), WorldBatch.withAlpha(mix(argb, 0xFFFFFFFF, 0.3f), 0.35f),
                    0.36, 0.8, false, false, false, true, true);
            case 3 -> new Look(argb, WorldBatch.withAlpha(argb, 0.2f),
                    0.18, 1.0, false, true, false, false, false);
            case 4 -> new Look(mix(argb, 0xFF000000, 0.6f), WorldBatch.withAlpha(mix(argb, 0xFF000000, 0.6f), 0.45f),
                    0.2, 1.1, false, false, true, false, false);
            default -> new Look(mix(argb, 0xFFFFFFFF, 0.45f), WorldBatch.withAlpha(argb, 0.5f),
                    0.2, 1.0, false, false, false, false, false);
        };
    }

    // ---------- toán và màu ----------

    private static WorldBatch.V cross(WorldBatch.V a, WorldBatch.V o) {
        return new WorldBatch.V(a.y() * o.z() - a.z() * o.y(),
                a.z() * o.x() - a.x() * o.z(),
                a.x() * o.y() - a.y() * o.x());
    }

    /** Vector đơn vị, hoặc null nếu vector gần bằng 0 */
    private static WorldBatch.V unit(WorldBatch.V v) {
        double len = Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
        if (len < 1e-9) return null;
        return v.mul(1 / len);
    }

    /** Trộn hai màu ARGB theo f từ 0 đến 1 */
    private static int mix(int a, int b, float f) {
        float k = Math.max(0f, Math.min(1f, f));
        int aa = Math.round(((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * k);
        int rr = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int gg = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bb = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    private static int darken(int argb, float k) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.round(((argb >> 16) & 0xFF) * k);
        int g = Math.round(((argb >> 8) & 0xFF) * k);
        int bl = Math.round((argb & 0xFF) * k);
        return (a << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
