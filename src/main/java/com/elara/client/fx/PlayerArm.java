package com.elara.client.fx;

import net.minecraft.client.util.math.MatrixStack;

/**
 * Cánh tay người chơi trong góc nhìn thứ nhất, vẽ bằng khối trong WorldBatch.
 * Vì hàm vẽ gốc của tay bị tắt khi có vũ khí 3D, tay này thay thế nó.
 *
 * Tay được tô bằng màu da cố định, chưa lấy texture skin: lấy texture cần API của
 * SkinTextures, và API đó chưa kiểm chứng được khi build cục bộ. Ống tay dài tới khuỷu,
 * phần gần khuỷu là tay áo.
 */
public final class PlayerArm {
    private static final int SKIN = 0xFFEBC09B;
    private static final int SLEEVE = 0xFF3E5A8C;
    /** Phần tay áo bắt đầu từ tỉ lệ này của chiều dài cánh tay */
    private static final double SLEEVE_AT = 0.45;
    private static final double LENGTH = 1.0;
    private static final WorldBatch.V UP = new WorldBatch.V(0, 1, 0);

    private PlayerArm() {}

    /**
     * Vẽ cánh tay trong hệ tọa độ hiện tại của m. Gốc là vị trí tay cầm vũ khí.
     * side = 1 cho tay phải, -1 cho tay trái.
     */
    public static void draw(MatrixStack m, float side) {
        WorldBatch.V axis = unit(new WorldBatch.V(0.30 * side, -0.55, 0.75));
        WorldBatch.V p = unit(cross(axis, UP));
        WorldBatch.V q = unit(cross(p, axis));

        // Cổ tay nằm lùi một chút sau tay cầm để vũ khí che mép tay
        WorldBatch.V wrist = axis.mul(-0.02);
        WorldBatch.V mid = wrist.add(axis.mul(LENGTH * SLEEVE_AT));
        WorldBatch.V elbow = wrist.add(axis.mul(LENGTH));

        WorldBatch b = new WorldBatch(m.peek().getPositionMatrix(), 0, 0, 0, false);
        tube(b, wrist, 0.040, mid, 0.050, p, q, SKIN);
        tube(b, mid, 0.050, elbow, 0.062, p, q, SLEEVE);
        b.flush();
    }

    /** Một đoạn ống vuông, bốn mặt bên. Mặt đối diện theo p tối hơn để thấy khối. */
    private static void tube(WorldBatch b, WorldBatch.V c0, double w0, WorldBatch.V c1, double w1,
                             WorldBatch.V p, WorldBatch.V q, int argb) {
        WorldBatch.V[] s0 = corners(c0, w0, p, q);
        WorldBatch.V[] s1 = corners(c1, w1, p, q);
        int dark = darken(argb, 0.70f);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            int col = (i % 2 == 0) ? argb : dark;
            b.quad(s0[i], s0[j], s1[j], s1[i], col);
        }
    }

    /** Bốn góc của một tiết diện vuông: (+p+q), (+p-q), (-p-q), (-p+q) */
    private static WorldBatch.V[] corners(WorldBatch.V c, double w, WorldBatch.V p, WorldBatch.V q) {
        WorldBatch.V pw = p.mul(w);
        WorldBatch.V qw = q.mul(w);
        return new WorldBatch.V[] {
                c.add(pw).add(qw),
                c.add(pw).sub(qw),
                c.sub(pw).sub(qw),
                c.sub(pw).add(qw)
        };
    }

    private static int darken(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int bl = (int) ((argb & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | bl;
    }

    private static WorldBatch.V unit(WorldBatch.V v) {
        double len = Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
        return len < 1e-9 ? new WorldBatch.V(1, 0, 0) : v.mul(1.0 / len);
    }

    private static WorldBatch.V cross(WorldBatch.V a, WorldBatch.V b) {
        return new WorldBatch.V(a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x());
    }
}
