package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.fx.WorldBatch;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Cảnh 3D làm nền màn hình chính. Ở màn hình chính chưa có thế giới, nên cảnh này tự chiếu
 * phối cảnh các tam giác (hồ, cây, phản chiếu) lên màn hình GUI. Bầu trời và mặt đất vẽ bằng
 * hình chữ nhật, cây và hồ vẽ bằng tam giác đã sắp theo độ sâu (xa trước, gần sau).
 * Lỗi bất kỳ thì tự tắt và trả về nền tĩnh.
 */
public final class TitleScene {
    private static final double HALF_FOV = 0.95;   // tiêu cự = chiều cao màn hình nhân hệ số này
    private static final double CAM_RADIUS = 8.0;
    private static final double CAM_HEIGHT = 2.6;
    private static final double FOCUS_Y = 1.2;
    private static final double POND_R = 2.6;
    private static final int POND_SEGS = 16;
    /** Số hạt sao tối đa ở màn hình chính: giữ thấp để máy điện thoại không nóng */
    private static final int STAR_COUNT = 20;
    private static final float REFLECT_ALPHA = 0.32f;

    /** Màu trời theo giờ: {đỉnh, chân trời} */
    private static final int[][] SKY = {
            {0xFF2A3570, 0xFFFFA58A},  // bình minh
            {0xFF3D8BFF, 0xFFCFEAFF},  // trưa
            {0xFF3B1E63, 0xFFFF8A65},  // hoàng hôn
            {0xFF050A1F, 0xFF1C2446},  // đêm
    };
    private static final int[] GROUND = {0xFF3E6B4A, 0xFF4F9D3A, 0xFF3A4A2C, 0xFF0F1F1A};
    private static final int[] WATER = {0xFF4E7FA8, 0xFF6FB7E6, 0xFF8A4F7A, 0xFF10203A};
    private static final float[] DIM = {0.85f, 1.0f, 0.8f, 0.5f};
    /** Màu tán lá theo loại cây: anh đào, sồi, thông */
    private static final int[][] FOLIAGE = {
            {0xFFFFB7C5, 0xFFF7A1B8, 0xFFFFD1DC},
            {0xFF3F9B3F, 0xFF2E7D32, 0xFF56B150},
            {0xFF1F5E3A, 0xFF15462B},
    };
    private static final int[] TRUNK = {0xFF6B4A3A, 0xFF6B4A3A, 0xFF4A3A2A};

    private record Tree(double x, double z, double s, int seed) {}

    /** Một tam giác đã chiếu lên màn hình, kèm độ sâu trung bình để sắp xếp */
    private record Tri(double x0, double y0, double x1, double y1, double x2, double y2, double depth, int color) {}

    private static final List<Tree> TREES = buildTrees();
    private static final double[][] STARS = buildStars();
    private static boolean failed = false;

    private TitleScene() {}

    /** Vẽ cảnh nền. Trả về false nếu không vẽ được, lúc đó người gọi vẽ ảnh tĩnh. */
    public static boolean draw(DrawContext ctx, int w, int h) {
        if (failed) return false;
        try {
            render(ctx, w, h);
            return true;
        } catch (RuntimeException e) {
            failed = true;
            System.err.println("[Elara] Cảnh 3D lỗi, chuyển về ảnh tĩnh: " + e);
            return false;
        }
    }

    private static void render(DrawContext ctx, int w, int h) {
        int time = clampInt(ElaraConfig.sceneTime(), 0, 3);
        int type = clampInt(ElaraConfig.sceneTree(), 0, 2);
        double t = System.currentTimeMillis() / 1000.0;
        View v = new View(angleFor(ElaraConfig.sceneCam(), t), w, h);
        int horizon = clampInt((int) Math.round(v.horizonY()), h / 5, h * 9 / 10);

        drawSky(ctx, w, horizon, time, t);
        ctx.fill(0, horizon, w, h, GROUND[time]);
        ctx.draw(); // đẩy hết hình chữ nhật ra trước, rồi mới tới tam giác

        List<Tri> pond = new ArrayList<>();
        addPond(pond, v, time, t);

        List<Tri> body = new ArrayList<>();
        for (Tree tr : TREES) {
            addTree(body, v, tr, type, time, t, -1, REFLECT_ALPHA); // bóng phản chiếu dưới mặt nước
            addTree(body, v, tr, type, time, t, 1, 1f);
        }
        body.sort(Comparator.comparingDouble(Tri::depth).reversed());

        WorldBatch b = new WorldBatch(ctx.getMatrices().peek().getPositionMatrix(), 0, 0, 0, false);
        emit(b, pond);
        emit(b, body);
        b.flush();
    }

    // ---------- trời ----------

    private static void drawSky(DrawContext ctx, int w, int horizon, int time, double t) {
        int top = SKY[time][0];
        int bottom = SKY[time][1];
        for (int y = 0; y < horizon; y += 2) {
            float k = y / (float) Math.max(1, horizon);
            ctx.fill(0, y, w, Math.min(horizon, y + 2), IosStyle.lerpColor(top, bottom, k));
        }
        if (time == 3) {
            for (int i = 0; i < STARS.length; i++) {
                int sx = (int) (STARS[i][0] * w);
                int sy = (int) (STARS[i][1] * horizon);
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.7 + i * 1.3);
                ctx.fill(sx, sy, sx + 1, sy + 1, IosStyle.withAlpha(0xFFFFFF, 0.25f + 0.6f * tw));
            }
        }
        drawCelestial(ctx, w, horizon, time);
    }

    /** Mặt trời buổi sáng và chiều, trăng ban đêm */
    private static void drawCelestial(DrawContext ctx, int w, int horizon, int time) {
        double sx;
        double sy;
        int color;
        switch (time) {
            case 0 -> { sx = 0.2; sy = 0.82; color = 0xFFFFD08A; }
            case 1 -> { sx = 0.72; sy = 0.22; color = 0xFFFFF3C4; }
            case 2 -> { sx = 0.84; sy = 0.86; color = 0xFFFFA360; }
            default -> { sx = 0.74; sy = 0.3; color = 0xFFE8ECFF; }
        }
        int r = Math.max(4, Math.min(w, horizon) / 16);
        int cx = (int) (sx * w);
        int cy = (int) (sy * horizon);
        IosStyle.rounded(ctx, cx - r, cy - r, r * 2, r * 2, r, color);
    }

    // ---------- hồ ----------

    private static void addPond(List<Tri> out, View v, int time, double t) {
        int water = IosStyle.withAlpha(WATER[time], 0.88f);
        double[] center = {0, 0, 0};
        for (int i = 0; i < POND_SEGS; i++) {
            double a0 = 2 * Math.PI * i / POND_SEGS;
            double a1 = 2 * Math.PI * (i + 1) / POND_SEGS;
            addTri(out, v, center, point(a0, POND_R, 0), point(a1, POND_R, 0), water);
        }
        // Vân sóng: một vành sáng nhấp nhô ở giữa hồ
        double ripple = 0.55 * POND_R + 0.12 * Math.sin(t * 1.5);
        int rc = IosStyle.withAlpha(0xFFFFFF, 0.16f + 0.08f * (float) Math.sin(t * 2.1));
        for (int i = 0; i < POND_SEGS; i++) {
            double a0 = 2 * Math.PI * i / POND_SEGS;
            double a1 = 2 * Math.PI * (i + 1) / POND_SEGS;
            addQuad(out, v, point(a0, ripple - 0.06, 0), point(a0, ripple + 0.06, 0),
                    point(a1, ripple + 0.06, 0), point(a1, ripple - 0.06, 0), rc);
        }
    }

    private static double[] point(double ang, double r, double y) {
        return new double[] {Math.cos(ang) * r, y, Math.sin(ang) * r};
    }

    // ---------- cây ----------

    /** Tính điểm của cây: dx, dz là lệch so với gốc, sway làm tán cây đung đưa theo độ cao */
    private static double[] pt(Tree tr, double dx, double y, double dz, double ym, double sway) {
        return new double[] {tr.x() + dx + sway * y, y * ym, tr.z() + dz};
    }

    /** Thêm một cây. ym = -1 để vẽ bóng phản chiếu dưới nước, alphaK là độ trong suốt */
    private static void addTree(List<Tri> out, View v, Tree tr, int type, int time, double t,
                                double ym, float alphaK) {
        double s = tr.s();
        double sway = 0.05 * Math.sin(t * 1.2 + tr.seed() * 0.7);
        float dim = DIM[time];
        double th = (type == 2 ? 0.9 : 1.0) * s;
        double hw = 0.09 * s;

        // thân: hình hộp bốn mặt, mặt sau tối hơn
        int trunk = tint(TRUNK[type], dim, alphaK);
        for (int i = 0; i < 4; i++) {
            double a0 = Math.PI / 4 + i * Math.PI / 2;
            double a1 = a0 + Math.PI / 2;
            double[] b0 = pt(tr, Math.cos(a0) * hw, 0, Math.sin(a0) * hw, ym, 0);
            double[] b1 = pt(tr, Math.cos(a1) * hw, 0, Math.sin(a1) * hw, ym, 0);
            double[] t0 = pt(tr, Math.cos(a0) * hw, th, Math.sin(a0) * hw, ym, sway);
            double[] t1 = pt(tr, Math.cos(a1) * hw, th, Math.sin(a1) * hw, ym, sway);
            float side = 0.7f + 0.3f * (float) Math.cos(a0 + Math.PI / 4);
            addQuad(out, v, b0, b1, t1, t0, tint(TRUNK[type], dim * side, alphaK));
        }

        if (type == 2) {
            addPine(out, v, tr, s, th, dim, alphaK, ym, sway);
        } else {
            addBlobs(out, v, tr, type, s, th, dim, alphaK, ym, sway);
        }
    }

    /** Tán lá anh đào và sồi: ba khối bát diện chồng nhau */
    private static void addBlobs(List<Tri> out, View v, Tree tr, int type, double s, double th,
                                 float dim, float alphaK, double ym, double sway) {
        int[] pal = FOLIAGE[type];
        double[][] blobs = {
                {0, th + 0.35 * s, 0, 0.5 * s},
                {-0.3 * s, th + 0.15 * s, 0.2 * s, 0.38 * s},
                {0.15 * s, th + 0.55 * s, -0.2 * s, 0.36 * s},
        };
        int seedPhase = (tr.seed() & 7);
        for (int bi = 0; bi < blobs.length; bi++) {
            double cx = blobs[bi][0];
            double cy = blobs[bi][1];
            double cz = blobs[bi][2];
            double r = blobs[bi][3];
            double[] top = pt(tr, cx, cy + r, cz, ym, sway);
            double[] bot = pt(tr, cx, cy - r, cz, ym, sway);
            double[][] eq = new double[4][];
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + seedPhase * 0.2;
                eq[k] = pt(tr, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r, ym, sway);
            }
            for (int k = 0; k < 4; k++) {
                int base = pal[(bi + k) % pal.length];
                int lit = tint(base, dim * 1.12f, alphaK);
                int shade = tint(base, dim * 0.82f, alphaK);
                addTri(out, v, top, eq[k], eq[(k + 1) % 4], lit);
                addTri(out, v, bot, eq[(k + 1) % 4], eq[k], shade);
            }
        }
    }

    /** Thông: ba tầng nón, mỗi nón tám cạnh */
    private static void addPine(List<Tri> out, View v, Tree tr, double s, double th, float dim,
                                float alphaK, double ym, double sway) {
        int[] pal = FOLIAGE[2];
        double[][] cones = {
                {0.3 * s, 0.75 * s, 0.8 * s},
                {0.8 * s, 0.6 * s, 0.7 * s},
                {1.2 * s, 0.42 * s, 0.6 * s},
        };
        for (double[] c : cones) {
            double by = Math.max(c[0], th * 0.5);
            double r = c[1];
            double[] apex = pt(tr, 0, by + c[2], 0, ym, sway);
            int n = 8;
            double[][] ring = new double[n][];
            for (int k = 0; k < n; k++) {
                double a = 2 * Math.PI * k / n;
                ring[k] = pt(tr, Math.cos(a) * r, by, Math.sin(a) * r, ym, sway);
            }
            for (int k = 0; k < n; k++) {
                int col = tint(pal[k % 2], dim * (k % 2 == 0 ? 1.0f : 0.84f), alphaK);
                addTri(out, v, apex, ring[k], ring[(k + 1) % n], col);
            }
        }
    }

    // ---------- chiếu ----------

    private static void addTri(List<Tri> out, View v, double[] a, double[] b, double[] c, int color) {
        double[] pa = new double[2];
        double[] pb = new double[2];
        double[] pc = new double[2];
        double da = v.project(a, pa);
        double db = v.project(b, pb);
        double dc = v.project(c, pc);
        if (da < 0 || db < 0 || dc < 0) return;
        out.add(new Tri(pa[0], pa[1], pb[0], pb[1], pc[0], pc[1], (da + db + dc) / 3, color));
    }

    private static void addQuad(List<Tri> out, View v, double[] a, double[] b, double[] c, double[] d, int color) {
        addTri(out, v, a, b, c, color);
        addTri(out, v, a, c, d, color);
    }

    private static void emit(WorldBatch b, List<Tri> tris) {
        for (Tri t : tris) {
            b.tri(new WorldBatch.V(t.x0(), t.y0(), 0), new WorldBatch.V(t.x1(), t.y1(), 0),
                    new WorldBatch.V(t.x2(), t.y2(), 0), t.color());
        }
    }

    /** Góc quay camera quanh cây theo chế độ: 0 xoay vòng, 1 trôi nhẹ, 2 đứng yên */
    private static double angleFor(int mode, double t) {
        return switch (mode) {
            case 0 -> 0.6 + t * 0.08;
            case 1 -> 0.6 + 0.35 * Math.sin(t * 0.3);
            default -> 0.6;
        };
    }

    /** Nhân màu với hệ số sáng và độ trong suốt. Kết quả giữ trong khoảng 0..255. */
    private static int tint(int argb, float dim, float alphaK) {
        int a = clampInt(Math.round(((argb >>> 24) & 0xFF) * alphaK), 0, 255);
        int r = clampInt(Math.round(((argb >> 16) & 0xFF) * dim), 0, 255);
        int g = clampInt(Math.round(((argb >> 8) & 0xFF) * dim), 0, 255);
        int bl = clampInt(Math.round((argb & 0xFF) * dim), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static List<Tree> buildTrees() {
        Random r = new Random(7);
        List<Tree> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            double ang = i * 2 * Math.PI / 7 + r.nextDouble() * 0.4;
            double rad = 2.9 + r.nextDouble() * 1.7;
            out.add(new Tree(Math.cos(ang) * rad, Math.sin(ang) * rad, 0.85 + r.nextDouble() * 0.35, r.nextInt(1000)));
        }
        return out;
    }

    private static double[][] buildStars() {
        Random r = new Random(11);
        double[][] s = new double[STAR_COUNT][2];
        for (double[] p : s) {
            p[0] = r.nextDouble();
            p[1] = r.nextDouble();
        }
        return s;
    }

    /** Camera nhìn vào tâm hồ. Mọi cây nằm trong bán kính 4.6 nên luôn ở trước mắt camera. */
    private static final class View {
        final double ex;
        final double ey;
        final double ez;
        final double[] f = new double[3];
        final double[] r = new double[3];
        final double[] u = new double[3];
        final double focal;
        final double cx;
        final double cy;

        View(double angle, int w, int h) {
            ex = CAM_RADIUS * Math.cos(angle);
            ey = CAM_HEIGHT;
            ez = CAM_RADIUS * Math.sin(angle);
            double fx = -ex;
            double fy = FOCUS_Y - ey;
            double fz = -ez;
            double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
            f[0] = fx / fl;
            f[1] = fy / fl;
            f[2] = fz / fl;
            // r = f x (0,1,0), u = r x f: hệ trục vuông góc, u hướng lên
            double rx = -f[2];
            double rz = f[0];
            double rl = Math.sqrt(rx * rx + rz * rz);
            r[0] = rx / rl;
            r[1] = 0;
            r[2] = rz / rl;
            u[0] = r[1] * f[2] - r[2] * f[1];
            u[1] = r[2] * f[0] - r[0] * f[2];
            u[2] = r[0] * f[1] - r[1] * f[0];
            focal = h * HALF_FOV;
            cx = w / 2.0;
            cy = h * 0.56;
        }

        /** Chiếu một điểm lên màn hình. Trả về độ sâu, hoặc -1 nếu điểm nằm sau camera. */
        double project(double[] p, double[] out) {
            double dx = p[0] - ex;
            double dy = p[1] - ey;
            double dz = p[2] - ez;
            double depth = dx * f[0] + dy * f[1] + dz * f[2];
            if (depth < 0.2) return -1;
            double xr = dx * r[0] + dy * r[1] + dz * r[2];
            double yu = dx * u[0] + dy * u[1] + dz * u[2];
            out[0] = cx + focal * xr / depth;
            out[1] = cy - focal * yu / depth;
            return depth;
        }

        /** Đường chân trời: chiếu một điểm rất xa theo hướng nhìn ngang */
        double horizonY() {
            double[] o = new double[2];
            double[] far = {ex + f[0] * 400, 0, ez + f[2] * 400};
            double d = project(far, o);
            return d < 0 ? cy : o[1];
        }
    }
}
