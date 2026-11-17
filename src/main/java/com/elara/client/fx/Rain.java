package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;

/**
 * Mưa 3D: các vạch mưa rơi quanh camera trong một khối 28x24x28 block.
 * Mật độ và tốc độ chỉnh được. Vạch luôn quay mặt về camera.
 */
public final class Rain {
    private static final double RANGE = 14.0;
    private static final double HEIGHT = 24.0;
    private static final double LEN = 0.9;
    private static final double WIDTH = 0.04;

    private Rain() {}

    public static void draw(WorldBatch b, WorldBatch.V cam, ElaraConfig.Layout l, int argb) {
        int count = Math.max(20, Math.min(300, l.get("density", 120)));
        int speed = Math.max(1, Math.min(10, l.get("speed", 5)));
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < count; i++) {
            double x = cam.x() + (hash(i, 1) - 0.5) * 2 * RANGE;
            double z = cam.z() + (hash(i, 2) - 0.5) * 2 * RANGE;
            double fall = (hash(i, 3) + t * speed * 0.6) % 1.0;
            double yTop = cam.y() + HEIGHT / 2 - fall * HEIGHT;
            b.billboardBeam(new WorldBatch.V(x, yTop, z), new WorldBatch.V(x, yTop - LEN, z), cam, WIDTH, argb);
        }
    }

    /** Số giả ngẫu nhiên cố định cho từng hạt mưa, nằm trong [0, 1) */
    private static double hash(int i, int salt) {
        double r = Math.sin(i * 127.1 + salt * 311.7) * 43758.5453;
        return r - Math.floor(r);
    }
}
