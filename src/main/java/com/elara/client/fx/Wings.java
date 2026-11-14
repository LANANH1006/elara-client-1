package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.UUID;

/**
 * Cánh ảo 3D nhiều lớp. Mỗi cánh có các khớp (gốc, khuỷu, đầu) và lông vũ
 * là các dải nối giữa khớp, vỗ theo thời gian thực.
 * Kiểu: 0 = thiên thần, 1 = rồng/quỷ (mép răng cưa), 2 = bướm, 3 = cyber/holo (khung dây).
 */
public final class Wings {
    /** Các khớp (u dọc theo cánh, v lên trên) của từng kiểu, trước khi nhân với kích thước */
    private static final float[][][] JOINTS = {
            {{0f, 0f}, {0.55f, 0.35f}, {1.05f, 0.2f}, {1.25f, -0.1f}},
            {{0f, 0f}, {0.45f, 0.55f}, {0.7f, 0.2f}, {1.0f, 0.7f}, {1.15f, 0.25f}},
            {{0f, 0f}, {0.8f, 0.65f}, {1.0f, 0.2f}, {0.55f, -0.15f}},
            {{0f, 0f}, {0.9f, 0.4f}, {1.3f, 0.05f}},
    };
    /** Độ rộng lông (chiều sau) tại mỗi khớp, cùng số phần tử với JOINTS */
    private static final float[][] CHORD = {
            {0.3f, 0.28f, 0.2f, 0.08f},
            {0.26f, 0.22f, 0.16f, 0.14f, 0.06f},
            {0.42f, 0.36f, 0.25f, 0.2f},
            {0.2f, 0.2f, 0.2f},
    };
    /** Ba lớp lông xếp chồng: độ cao, tỉ lệ rộng, độ mờ */
    private static final double[] LAYER_LIFT = {0.0, 0.05, 0.1};
    private static final double[] LAYER_SCALE = {1.0, 0.8, 0.6};
    private static final float[] LAYER_ALPHA = {0.7f, 0.45f, 0.25f};

    private static final WorldBatch.V UP = new WorldBatch.V(0, 1, 0);

    private Wings() {}

    public static void draw(WorldBatch b, MinecraftClient client, ElaraConfig.Layout l, int argb, float a) {
        int style = Math.max(0, Math.min(3, l.get("style", 0)));
        double size = Math.max(0.5, l.scalePct / 100.0);
        int speed = Math.max(1, Math.min(10, l.get("speed", 5)));
        double tSec = System.currentTimeMillis() / 1000.0;
        double flap = 0.35 * Math.sin(tSec * (2.0 + speed * 0.6));
        double glow = 0.6 + 0.4 * Math.sin(tSec * 17.0);

        for (Map.Entry<UUID, PlayerTracker.Track> e : PlayerTracker.tracks().entrySet()) {
            PlayerTracker.Track t = e.getValue();
            double px = PlayerTracker.lerp(t.px, t.x, a);
            double py = PlayerTracker.lerp(t.py, t.y, a);
            double pz = PlayerTracker.lerp(t.pz, t.z, a);
            if (!Visibility.allows(l, client, e.getKey(), px, py, pz)) continue;

            double yr = Math.toRadians(t.yaw);
            // Quy ước của Minecraft: hướng nhìn (-sin, cos), phải = (-cos, -sin)
            WorldBatch.V right = new WorldBatch.V(-Math.cos(yr), 0, -Math.sin(yr));
            WorldBatch.V back = new WorldBatch.V(Math.sin(yr), 0, -Math.cos(yr));
            WorldBatch.V root = new WorldBatch.V(px, py + 1.35, pz).add(back.mul(0.2));
            for (int side = -1; side <= 1; side += 2) {
                Frame f = new Frame(root, right.mul(side), back, size);
                if (style == 3) {
                    cyber(b, f, flap, argb, glow);
                } else {
                    solid(b, f, style, flap, argb);
                }
            }
        }
    }

    /** Khung của một cánh: gốc, hướng dài theo cánh, hướng sau, kích thước */
    private record Frame(WorldBatch.V root, WorldBatch.V span, WorldBatch.V back, double size) {
        WorldBatch.V at(double u, double v, double lift, double depth) {
            return root.add(span.mul(u * size))
                    .add(UP.mul(v * size + lift))
                    .add(back.mul(depth * size));
        }
    }

    /** Cánh đặc: ba lớp lông, mỗi lớp là các dải nối giữa khớp liên tiếp */
    private static void solid(WorldBatch b, Frame f, int style, double flap, int argb) {
        float[][] j = JOINTS[style];
        float[] c = CHORD[style];
        for (int layer = 0; layer < LAYER_LIFT.length; layer++) {
            int col = scaleAlpha(argb, LAYER_ALPHA[layer]);
            double lift = LAYER_LIFT[layer];
            double sc = LAYER_SCALE[layer];
            for (int i = 0; i < j.length - 1; i++) {
                double uA = j[i][0];
                double vA = j[i][1] + flap * uA * 1.4;
                double uB = j[i + 1][0];
                double vB = j[i + 1][1] + flap * uB * 1.4;
                WorldBatch.V aF = f.at(uA, vA, lift, 0);
                WorldBatch.V bF = f.at(uB, vB, lift, 0);
                WorldBatch.V bB = f.at(uB, vB, lift, c[i + 1] * sc);
                WorldBatch.V aB = f.at(uA, vA, lift, c[i] * sc);
                b.quad(aF, bF, bB, aB, col);
            }
        }
    }

    /** Cyber/Holo: chỉ vẽ khung dây, nhấp nháy theo thời gian */
    private static void cyber(WorldBatch b, Frame f, double flap, int argb, double glow) {
        float[][] j = JOINTS[3];
        float[] c = CHORD[3];
        int col = scaleAlpha(argb, (float) glow);
        double w = 0.03 * f.size();
        for (int i = 0; i < j.length - 1; i++) {
            double uA = j[i][0];
            double vA = j[i][1] + flap * uA * 1.4;
            double uB = j[i + 1][0];
            double vB = j[i + 1][1] + flap * uB * 1.4;
            WorldBatch.V aF = f.at(uA, vA, 0, 0);
            WorldBatch.V bF = f.at(uB, vB, 0, 0);
            WorldBatch.V aB = f.at(uA, vA, 0, c[i]);
            WorldBatch.V bB = f.at(uB, vB, 0, c[i + 1]);
            b.beam(aF, bF, w, col);
            b.beam(aB, bB, w, col);
            b.beam(aF, aB, w, col);
        }
    }

    /** Giảm độ mờ của một màu ARGB theo hệ số k từ 0 đến 1 */
    private static int scaleAlpha(int argb, float k) {
        float kk = Math.max(0f, Math.min(1f, k));
        int alpha = (int) (((argb >>> 24) & 0xFF) * kk);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
