package com.elara.client.fx;

import com.elara.client.core.ElaraAssets;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;

/**
 * Cánh ảo. Có sáu kiểu: 0 thiên thần, 1 rồng (mép răng cưa), 2 bướm (một lớp, có đốm), 3 cyber (khung dây sáng),
 * 4 quạ đen (lông đen, khói bay lên), 5 tinh vân phát sáng (một tấm cánh liền khối có texture sinh bằng Python).
 *
 * Kiểu 0 đến 4 vẽ từng lông bằng texture wings_feather.png. Kiểu 5 vẽ một lưới SHEET_S x SHEET_T ô,
 * texture wings_galaxy.png ánh xạ theo (sải, dây cung) nên các lông liền thành một mặt cánh.
 *
 * Cánh gắn vào thân: theo bodyYaw (góc thân, không theo góc nhìn đầu), hạ thấp và ngả về trước khi cúi.
 * Ở góc nhìn thứ nhất không vẽ cánh của chính mình, để cánh không chĩa xuyên màn hình.
 */
public final class Wings {
    public static final int STYLES = 6;
    /** Kiểu tinh vân: tấm cánh liền khối */
    private static final int STYLE_GALAXY = 5;
    private static final int SEC = 4;
    /** Vị trí các khớp dọc theo cánh: 0 vai, 1 khuỷu, 2 cổ tay, 3 chóp */
    private static final double[] JOINT_U = {0.0, 0.36, 0.70, 1.0};
    /** Mỗi bên khoảng 1.45 block, sải cánh khoảng 2.9 block khi xòe */
    private static final double ARM_LEN = 1.45;
    /** Gốc cánh ở độ cao vai, tính từ chân người chơi */
    private static final double ROOT_Y = 1.40;
    /** Gốc cánh lùi ra sau thân, cách mặt lưng 0.16 block để không chạm áo choàng */
    private static final double ROOT_BACK = 0.16;
    /** Khi cúi: gốc cánh hạ xuống và ngả về trước */
    private static final double SNEAK_SINK = 0.30;
    private static final double SNEAK_FORWARD = 0.05;
    /** Số ô lưới của tấm cánh tinh vân: theo sải và theo dây cung */
    private static final int SHEET_S = 8;
    private static final int SHEET_T = 3;
    /**
     * Hệ số chiều cao của tấm tinh vân so với bản trước: 3.2 lần. Chỉ tác động phần cao (trục đứng),
     * sải ngang ARM_LEN giữ nguyên 1.45 block mỗi bên. Đặt 1.0 để trở về kích thước cũ.
     */
    private static final double GALAXY_HEIGHT = 3.2;
    private static final WorldBatch.V UP = new WorldBatch.V(0, 1, 0);

    /** Cách một kiểu cánh trông thế nào */
    private record Look(int base, int tip, double wMax, double lenK, boolean spiky, boolean wire,
                        boolean smoke, boolean spots, boolean single) {}

    private Wings() {}

    /** Vẽ cánh cho mọi người chơi đang theo dõi. Tự flush cả hai batch. */
    public static void draw(Matrix4f mat, MinecraftClient client, ElaraConfig.Layout l, int argb, float a,
                            WorldBatch.V cam) {
        if (!ElaraConfig.isOn(Feature.VIRTUAL_WINGS)) return;
        int style = clamp(l.get("style", 0), 0, STYLES - 1);
        // Ảnh chỉ nạp khi cánh đang được vẽ; ảnh lỗi thì không vẽ cánh, game vẫn chạy
        boolean texOk = style == STYLE_GALAXY ? ElaraAssets.ensureWingGalaxy() : ElaraAssets.ensureWingFeather();
        if (!texOk) return;
        double size = Math.max(0.5, l.scalePct / 100.0);
        int speed = clamp(l.get("speed", 5), 1, 10);
        double t = System.currentTimeMillis() / 1000.0;
        Look look = look(style, argb);
        Identifier tex = style == STYLE_GALAXY ? ElaraAssets.WING_GALAXY : ElaraAssets.WING_FEATHER;
        WorldBatch wb = new WorldBatch(mat, cam.x(), cam.y(), cam.z(), true);
        TexBatch tb = new TexBatch(mat, cam.x(), cam.y(), cam.z(), tex);
        boolean firstPerson = client.options.getPerspective().isFirstPerson();

        for (Map.Entry<UUID, PlayerTracker.Track> e : PlayerTracker.tracks().entrySet()) {
            // Góc nhìn thứ nhất: không vẽ cánh của chính mình
            if (firstPerson && client.player != null && e.getKey().equals(client.player.getUuid())) continue;
            PlayerTracker.Track tr = e.getValue();
            double px = PlayerTracker.lerp(tr.px, tr.x, a);
            double py = PlayerTracker.lerp(tr.py, tr.y, a);
            double pz = PlayerTracker.lerp(tr.pz, tr.z, a);
            if (!Visibility.allows(l, client, e.getKey(), px, py, pz)) continue;
            PlayerMotion.State m = PlayerMotion.get(e.getKey());
            if (m == null || m.hidden) continue;

            // Quy ước của Minecraft: phải = (-cos, -sin), sau = (sin, -cos). Theo góc thân, nội suy mượt.
            double yr = Math.toRadians(lerpDeg(tr.prevBodyYaw, tr.bodyYaw, a));
            WorldBatch.V right = new WorldBatch.V(-Math.cos(yr), 0, -Math.sin(yr));
            WorldBatch.V back = new WorldBatch.V(Math.sin(yr), 0, -Math.cos(yr));
            double sink = tr.sneaking ? SNEAK_SINK : 0.0;
            double fwd = tr.sneaking ? SNEAK_FORWARD : 0.0;
            WorldBatch.V root = new WorldBatch.V(px, py + ROOT_Y - sink, pz).add(back.mul(ROOT_BACK - fwd));

            // Vỗ: chậm khi đứng yên, nhịp nhanh hơn khi đi, tốc độ chỉnh được
            double freq = m.moving ? 2.0 + speed * 0.25 : 0.8;
            double phase = t * Math.PI * 2 * freq;
            for (int side = -1; side <= 1; side += 2) {
                WorldBatch.V span = right.mul(side);
                if (style == STYLE_GALAXY) {
                    drawSheet(tb, root, span, back, size, m.spread, m.fold, m.flap, phase, t, look.base());
                } else {
                    drawWing(wb, tb, look, root, span, back, size, m.spread, m.fold, m.flap,
                            phase, t, cam, style);
                }
            }
        }
        wb.flush();
        tb.flush();
    }

    /**
     * Tấm cánh liền khối cho kiểu tinh vân. Lưới (SHEET_S+1) x (SHEET_T+1) điểm. Điểm (s, t) nằm trên
     * đường gốc cánh theo s, lùi về sau theo dây cung theo t. Texture có alpha cắt đúng hình cánh.
     */
    private static void drawSheet(TexBatch tb, WorldBatch.V root, WorldBatch.V span, WorldBatch.V back,
                                  double size, float spread, float fold, float amp, double phase, double t,
                                  int tint) {
        WorldBatch.V[][] p = new WorldBatch.V[SHEET_S + 1][SHEET_T + 1];
        for (int i = 0; i <= SHEET_S; i++) {
            double s = i / (double) SHEET_S;
            double lateral = size * ARM_LEN * (0.35 + 0.65 * spread) * s;
            double lift = GALAXY_HEIGHT * size * (0.28 * spread * Math.sin(Math.PI * s * 0.8) + 0.45 * spread * s * s * s);
            double wave = GALAXY_HEIGHT * amp * Math.sin(phase - s * 2.7) * s * size * 0.9;
            double backOff = size * (0.10 * s * s + 0.22 * fold * s + 0.20 * spread * s * s);
            WorldBatch.V base = root.add(span.mul(lateral)).add(UP.mul(lift + wave)).add(back.mul(backOff));
            // Dây cung dài dần ra chóp, hạ xuống và lùi về sau (phần cao nhân GALAXY_HEIGHT)
            double chordLen = size * (0.35 + 0.65 * s) * (0.6 + 0.4 * spread);
            WorldBatch.V chord = back.mul(chordLen * 0.9).add(UP.mul(-chordLen * 0.35 * GALAXY_HEIGHT));
            for (int j = 0; j <= SHEET_T; j++) {
                p[i][j] = base.add(chord.mul(j / (double) SHEET_T));
            }
        }
        // Màu nhấp nháy nhẹ theo thời gian, cả tấm cùng một màu
        int col = WorldBatch.withAlpha(tint, (float) (0.85 + 0.15 * Math.sin(t * 2.0)));
        for (int i = 0; i < SHEET_S; i++) {
            for (int j = 0; j < SHEET_T; j++) {
                float u0 = i / (float) SHEET_S;
                float u1 = (i + 1) / (float) SHEET_S;
                float v0 = j / (float) SHEET_T;
                float v1 = (j + 1) / (float) SHEET_T;
                tb.quadUv(p[i][j], p[i + 1][j], p[i + 1][j + 1], p[i][j + 1],
                        u0, v0, u1, v0, u1, v1, u0, v1, col, UP);
            }
        }
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
            // Bướm: một lớp lông tròn, dài, xòe ra SAU lưng (back dương là phía sau)
            for (int k = 1; k < n; k++) {
                WorldBatch.V dir = back.mul(0.6).add(UP.mul(0.45)).add(span.mul(0.5));
                feather(wb, tb, look, joint[k], dir, size * look.lenK() * 0.9, look.wMax() * 1.1, 0.02, k, cam, size);
            }
            return;
        }

        // Bốn lớp, từ ngoài vào trong. back dương nghĩa là lông xòe ra sau lưng.
        double[][] layers = {
                {1.0, -0.12, 0.22, 1.0},
                {0.85, 0.22, 0.10, 0.80},
                {0.55, 0.50, 0.00, 0.58},
                {0.30, 0.70, -0.05, 0.42},
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
                    0.24, 1.2, true, false, false, false, false);
            case 2 -> new Look(WorldBatch.withAlpha(argb, 0.7f), WorldBatch.withAlpha(mix(argb, 0xFFFFFFFF, 0.3f), 0.35f),
                    0.40, 0.8, false, false, false, true, true);
            case 3 -> new Look(argb, WorldBatch.withAlpha(argb, 0.2f),
                    0.18, 1.0, false, true, false, false, false);
            case 4 -> new Look(mix(argb, 0xFF000000, 0.6f), WorldBatch.withAlpha(mix(argb, 0xFF000000, 0.6f), 0.45f),
                    0.28, 1.1, false, false, true, false, false);
            case STYLE_GALAXY -> new Look(argb, WorldBatch.withAlpha(argb, 0.5f),
                    0.0, 1.0, false, false, false, false, false);
            default -> new Look(mix(argb, 0xFFFFFFFF, 0.45f), WorldBatch.withAlpha(argb, 0.5f),
                    0.28, 1.0, false, false, false, false, false);
        };
    }

    // ---------- toán và màu ----------

    /** Nội suy góc độ theo đường ngắn nhất, không nhảy qua 0 khi quay quanh 360 độ */
    private static double lerpDeg(double a, double b, double t) {
        double d = ((b - a) % 360.0 + 540.0) % 360.0 - 180.0;
        return a + d * t;
    }

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
