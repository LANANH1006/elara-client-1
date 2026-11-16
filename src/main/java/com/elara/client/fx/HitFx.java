package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Hiệu ứng khi đánh trúng: nổ tia sáng, ngôi sao, trái tim hoặc tia lửa tại thực thể bị đánh.
 * Các hình luôn quay mặt về camera.
 */
public final class HitFx {
    public static final int RAYS = 0;
    public static final int STARS = 1;
    public static final int HEARTS = 2;
    public static final int SPARKS = 3;

    private static final long DURATION_MS = 450L;
    private static final WorldBatch.V UP = new WorldBatch.V(0, 1, 0);
    private static final List<Burst> BURSTS = new ArrayList<>();

    private record Burst(double x, double y, double z, long startMs) {}

    private HitFx() {}

    /** Gọi khi người chơi đánh trúng một thực thể */
    public static void spawn(Entity e) {
        BURSTS.add(new Burst(e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ(), System.currentTimeMillis()));
        if (BURSTS.size() > 40) BURSTS.remove(0);
    }

    /** Gọi mỗi tick: bỏ các lần nổ đã hết thời gian */
    public static void tick() {
        if (!ElaraConfig.isOn(Feature.HIT_EFFECT)) {
            BURSTS.clear();
            return;
        }
        long now = System.currentTimeMillis();
        BURSTS.removeIf(b -> now - b.startMs() > DURATION_MS);
    }

    public static void draw(WorldBatch b, WorldBatch.V cam, ElaraConfig.Layout l, int argb) {
        if (!ElaraConfig.isOn(Feature.HIT_EFFECT)) return;
        int style = l.get("style", RAYS);
        long now = System.currentTimeMillis();
        for (Burst bu : BURSTS) {
            long age = now - bu.startMs();
            if (age < 0 || age >= DURATION_MS) continue;

            float t = age / (float) DURATION_MS;
            int col = WorldBatch.withAlpha(argb, (float) Math.pow(1 - t, 1.5));
            WorldBatch.V c = new WorldBatch.V(bu.x(), bu.y(), bu.z());
            WorldBatch.V toCam = normalize(cam.sub(c));
            WorldBatch.V right = normalize(cross(UP, toCam));
            WorldBatch.V up = cross(toCam, right);
            switch (style) {
                case STARS -> star(b, c, right, up, t, col);
                case HEARTS -> heart(b, c, right, up, t, col);
                case SPARKS -> sparks(b, c, right, up, t, col, cam);
                default -> rays(b, c, right, up, t, col, cam);
            }
        }
    }

    private static void rays(WorldBatch b, WorldBatch.V c, WorldBatch.V right, WorldBatch.V up,
                             float t, int col, WorldBatch.V cam) {
        double len = 0.15 + t * 0.9;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            WorldBatch.V dir = right.mul(Math.cos(a)).add(up.mul(Math.sin(a)));
            b.billboardBeam(c.add(dir.mul(0.1)), c.add(dir.mul(len)), cam, 0.05, col);
        }
    }

    /** Ngôi sao năm cánh, xoay chậm và nở ra */
    private static void star(WorldBatch b, WorldBatch.V c, WorldBatch.V right, WorldBatch.V up,
                             float t, int col) {
        double outer = 0.2 + t * 0.6;
        double rot = t * Math.PI;
        WorldBatch.V[] pts = new WorldBatch.V[10];
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? outer : outer * 0.42;
            double a = rot + i * Math.PI / 5;
            pts[i] = c.add(right.mul(r * Math.cos(a))).add(up.mul(r * Math.sin(a)));
        }
        for (int i = 0; i < 10; i++) {
            b.tri(c, pts[i], pts[(i + 1) % 10], col);
        }
    }

    /** Trái tim theo đường cong tham số, nở ra rồi mờ dần */
    private static void heart(WorldBatch b, WorldBatch.V c, WorldBatch.V right, WorldBatch.V up,
                              float t, int col) {
        double s = (0.15 + t * 0.35) / 16.0;
        int n = 24;
        WorldBatch.V[] pts = new WorldBatch.V[n];
        for (int i = 0; i < n; i++) {
            double th = 2 * Math.PI * i / n;
            double x = 16 * Math.pow(Math.sin(th), 3);
            double y = 13 * Math.cos(th) - 5 * Math.cos(2 * th) - 2 * Math.cos(3 * th) - Math.cos(4 * th);
            pts[i] = c.add(right.mul(x * s)).add(up.mul(y * s));
        }
        for (int i = 0; i < n; i++) {
            b.tri(c, pts[i], pts[(i + 1) % n], col);
        }
    }

    /** Tia lửa văng ra đều quanh điểm đánh, có rơi nhẹ */
    private static void sparks(WorldBatch b, WorldBatch.V c, WorldBatch.V right, WorldBatch.V up,
                               float t, int col, WorldBatch.V cam) {
        for (int k = 0; k < 12; k++) {
            double a = k * 2 * Math.PI / 12;
            double reach = (0.2 + 0.8 * frac(k * 0.618)) * t;
            WorldBatch.V p = c.add(right.mul(Math.cos(a) * reach)).add(up.mul(Math.sin(a) * reach))
                    .add(UP.mul(-0.4 * t * t));
            double s = 0.03 * (1 - t) + 0.01;
            WorldBatch.V q0 = p.add(right.mul(-s)).add(up.mul(-s));
            WorldBatch.V q1 = p.add(right.mul(s)).add(up.mul(-s));
            WorldBatch.V q2 = p.add(right.mul(s)).add(up.mul(s));
            WorldBatch.V q3 = p.add(right.mul(-s)).add(up.mul(s));
            b.quad(q0, q1, q2, q3, col);
        }
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }

    private static WorldBatch.V cross(WorldBatch.V a, WorldBatch.V b) {
        return new WorldBatch.V(a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x());
    }

    /** Chuẩn hóa vector. Vector bằng 0 thì trả về trục X để không chia cho 0. */
    private static WorldBatch.V normalize(WorldBatch.V v) {
        double len = Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
        if (len < 1e-9) return new WorldBatch.V(1, 0, 0);
        return v.mul(1 / len);
    }
}
