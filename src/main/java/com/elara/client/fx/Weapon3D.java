package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

/**
 * Vũ khí 3D ở góc nhìn thứ nhất. Thay kiếm gốc bằng mô hình vẽ bằng WorldBatch trong khung của vật cầm.
 *
 * Kiểu 0 (Dao CS2): dáng dao theo cấp kiếm. Gỗ: Gut Knife, Đá: Flip Knife, Sắt: M9 Bayonet,
 * Vàng: Butterfly, Kim cương: Karambit, Netherite: Skeleton.
 * Kiểu 1 (Katana): thanh katana có tsuba, tay cầm quấn chỉ và viền Radiance chạy dọc lưỡi.
 *
 * Hệ tọa độ: gốc là chân lưỡi, đúng vị trí tay cầm trong khung nhìn. DIR là hướng lưỡi,
 * SIDE là ngang lưỡi, NORM là pháp tuyến mặt lưỡi. Lớp phủ đặt ở z âm để nằm trước lưỡi.
 */
public final class Weapon3D {
    public static final int STYLES = 2;
    private static final int STYLE_KATANA = 1;

    /** Vẽ lỗi một lần thì tắt tới khi khởi động lại, để không báo lỗi mỗi khung hình */
    private static boolean broken = false;

    /** Các cấp dao theo kiểu CS2, cộng thêm Katana */
    private enum Tier { WOOD, STONE, IRON, GOLD, DIAMOND, NETHERITE, KATANA }

    /** Một lưỡi: các mặt cắt theo chiều dài u, mép dưới lo, mép trên hi, và độ cong c0 + a·t^p */
    private record Blade(double[] u, double[] lo, double[] hi, double c0, double a, double p) {
        double len() {
            return u[u.length - 1];
        }
    }

    private static final WorldBatch.V X_AXIS = new WorldBatch.V(1, 0, 0);
    private static final WorldBatch.V DIR = unit(new WorldBatch.V(-0.30, 0.55, -0.78));
    private static final WorldBatch.V SIDE = unit(X_AXIS.sub(DIR.mul(dot(X_AXIS, DIR))));
    private static final WorldBatch.V NORM = unit(cross(DIR, SIDE));

    // Hình dạng từng cấp, đơn vị là khối trong khung nhìn vật cầm
    private static final Blade GUT_BLADE = new Blade(
            new double[]{0, 0.05, 0.15, 0.25, 0.31, 0.34},
            new double[]{0, 0, 0, 0, 0, 0},
            new double[]{0.040, 0.050, 0.052, 0.042, 0.024, 0.0}, 0, 0, 1);
    private static final Blade FLIP_BLADE = new Blade(
            new double[]{0, 0.06, 0.18, 0.26, 0.30},
            new double[]{0, 0, 0, 0, 0},
            new double[]{0.046, 0.054, 0.046, 0.030, 0.0}, 0, 0, 1);
    private static final Blade M9_BLADE = new Blade(
            new double[]{0, 0.06, 0.20, 0.30, 0.36},
            new double[]{-0.046, -0.046, -0.042, -0.024, 0.0},
            new double[]{0, 0, 0, 0, 0}, 0, 0, 1);
    private static final Blade BUTTERFLY_BLADE = new Blade(
            new double[]{0, 0.08, 0.20, 0.30},
            new double[]{0, 0, 0, 0},
            new double[]{0.036, 0.042, 0.032, 0.0}, 0.02, 0.10, 2);
    private static final Blade KARAMBIT_BLADE = new Blade(
            new double[]{0, 0.06, 0.14, 0.21, 0.26, 0.29},
            new double[]{0, 0, 0, 0, 0, 0},
            new double[]{0.036, 0.046, 0.046, 0.040, 0.026, 0.0}, 0, 0.10, 3);
    private static final Blade SKELETON_BLADE = new Blade(
            new double[]{0, 0.10, 0.28, 0.36, 0.38},
            new double[]{-0.020, -0.024, -0.020, -0.010, 0.0},
            new double[]{0.020, 0.024, 0.020, 0.010, 0.0}, 0, 0, 1);
    private static final Blade KATANA_BLADE = new Blade(
            new double[]{0, 0.10, 0.40, 0.60, 0.66},
            new double[]{0, 0, 0, 0, 0},
            new double[]{0.032, 0.032, 0.030, 0.023, 0.0}, 0, 0.07, 2);

    private Weapon3D() {}

    /**
     * Vẽ vũ khí 3D thay cho kiếm gốc. Trả về true khi đã vẽ, khi đó bên gọi phải hủy bản vẽ gốc.
     * Hàm tự cân bằng push/pop của ma trận, kể cả khi lỗi.
     */
    public static boolean drawFirstPerson(MatrixStack m, AbstractClientPlayerEntity player, ItemStack stack,
                                          float swing, float equip) {
        if (broken || !ElaraConfig.isOn(Feature.WEAPON_3D) || !(stack.getItem() instanceof SwordItem)) {
            return false;
        }
        ElaraConfig.Layout l = ElaraConfig.layout(Feature.WEAPON_3D);
        int style = Math.max(0, Math.min(STYLES - 1, l.get("style", 0)));
        float size = Math.max(0.5f, l.scalePct / 100f);
        float side = player.getMainArm() == Arm.LEFT ? -1f : 1f;
        float spin = KnifeAnim.spinDegrees();
        Tier tier = tierOf(stack, style);
        boolean glint = stack.hasGlint();
        double pulse = 0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 1000.0 * 4.0);

        m.push();
        try {
            // Vị trí tay cầm trong khung nhìn thứ nhất, giống applyEquipOffset của game
            // Nâng tay cầm 0.12 so với game để chuôi nằm trong khung hình, vì không vẽ cánh tay
            m.translate(0.56f * side, -0.40f - equip * 0.6f, -0.72f);
            applySwing(m, side, swing);
            m.scale(size, size, size);

            // Vệt mờ khi xoay (Inspect): ba bản sao lùi sau, càng xa càng mờ
            if (spin != 0f) {
                for (int k = 3; k >= 1; k--) {
                    m.push();
                    try {
                        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin - 16f * k));
                        WorldBatch ghost = new WorldBatch(m.peek().getPositionMatrix(), 0, 0, 0, false);
                        paintSolid(ghost, tier, 0.35f / k);
                        ghost.flush();
                    } finally {
                        m.pop();
                    }
                }
            }

            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
            var mat = m.peek().getPositionMatrix();

            WorldBatch solid = new WorldBatch(mat, 0, 0, 0, false);
            paintSolid(solid, tier, 1f);
            solid.flush();

            // Lớp cộng sáng: ánh tím khi có hiệu ứng phù văn, viền Radiance cho Katana
            if (glint || tier == Tier.KATANA) {
                WorldBatch add = new WorldBatch(mat, 0, 0, 0, true);
                if (glint) glint(add, tier, pulse);
                if (tier == Tier.KATANA) radiance(add, pulse);
                add.flush();
            }
            return true;
        } catch (RuntimeException ex) {
            broken = true;
            System.err.println("[Elara] Tắt Vũ khí 3D do lỗi: " + ex);
            return false;
        } finally {
            m.pop();
        }
    }

    /** Biến đổi xoay của tay khi vung kiếm, theo cùng công thức với tay gốc của game */
    private static void applySwing(MatrixStack m, float side, float swing) {
        float f = (float) Math.sin(swing * swing * Math.PI);
        float g = (float) Math.sin(Math.sqrt(swing) * Math.PI);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45f - 20f * f)));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * g * -20f));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80f));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45f));
    }

    private static Tier tierOf(ItemStack stack, int style) {
        if (style == STYLE_KATANA) return Tier.KATANA;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return switch (path) {
            case "stone_sword" -> Tier.STONE;
            case "iron_sword" -> Tier.IRON;
            case "golden_sword" -> Tier.GOLD;
            case "diamond_sword" -> Tier.DIAMOND;
            case "netherite_sword" -> Tier.NETHERITE;
            default -> Tier.WOOD;
        };
    }

    private static Blade bladeOf(Tier t) {
        return switch (t) {
            case WOOD -> GUT_BLADE;
            case STONE -> FLIP_BLADE;
            case IRON -> M9_BLADE;
            case GOLD -> BUTTERFLY_BLADE;
            case DIAMOND -> KARAMBIT_BLADE;
            case NETHERITE -> SKELETON_BLADE;
            case KATANA -> KATANA_BLADE;
        };
    }

    // ---------- Các phần dựng hình ----------

    private static void paintSolid(WorldBatch b, Tier t, float k) {
        switch (t) {
            case WOOD -> {
                Blade bl = GUT_BLADE;
                strip(b, bl, bl.lo(), bl.hi(), 1, 0, fade(0xFFB9C3CF, k));
                handle(b, -0.10, 0.0, 0.034, fade(0xFF8B5A2B, k));
            }
            case STONE -> {
                Blade bl = FLIP_BLADE;
                strip(b, bl, bl.lo(), bl.hi(), 1, 0, fade(0xFF9AA4B0, k));
                ring(b, at(-0.03, -0.012, 0), 0.026, 0.034, 20, fade(0xFF5C6673, k));
                handle(b, -0.10, 0.0, 0.034, fade(0xFF2B3138, k));
            }
            case IRON -> {
                Blade bl = M9_BLADE;
                strip(b, bl, bl.lo(), bl.hi(), 1, 0, fade(0xFF5F6E80, k));
                // Lưng răng cưa: chín răng nhô ra phía sống dao
                for (int i = 0; i < 9; i++) {
                    double x0 = 0.07 + 0.025 * i;
                    b.tri(at(x0, 0, 0), at(x0 + 0.025, 0, 0), at(x0 + 0.025, 0.009, 0), fade(0xFF46505E, k));
                }
                box(b, -0.012, 0.008, -0.050, 0.020, -0.006, 0.006, fade(0xFF1E1E22, k));
                handle(b, -0.12, -0.030, 0.010, fade(0xFF1E1E22, k));
            }
            case GOLD -> {
                // Hai lưỡi xòe ra từ tay cầm, đối xứng qua mặt phẳng giữa
                Blade bl = BUTTERFLY_BLADE;
                strip(b, bl, bl.lo(), bl.hi(), 1, 0, fade(0xFFAEB8C4, k));
                strip(b, bl, bl.lo(), bl.hi(), -1, 0, fade(0xFFAEB8C4, k));
                handle(b, -0.10, -0.008, 0.008, fade(0xFFD9A62E, k));
            }
            case DIAMOND -> {
                Blade bl = KARAMBIT_BLADE;
                strip(b, bl, bl.lo(), bl.hi(), 1, 0, fade(0xFF7FE3F0, k));
                ring(b, at(-0.05, -0.040, 0), 0.020, 0.030, 20, fade(0xFF3E5566, k));
                handle(b, -0.10, -0.004, 0.030, fade(0xFF1F2A33, k));
            }
            case NETHERITE -> {
                Blade bl = SKELETON_BLADE;
                strip(b, bl, bl.lo(), bl.hi(), 1, 0, fade(0xFF3B3340, k));
                int hole = fade(0xFF0B0A0D, k);
                b.tri(at(0.10, 0, -0.002), at(0.20, -0.012, -0.002), at(0.20, 0.012, -0.002), hole);
                b.tri(at(0.24, 0, -0.002), at(0.31, -0.009, -0.002), at(0.31, 0.009, -0.002), hole);
                handle(b, -0.11, -0.012, 0.012, fade(0xFFE8E2D0, k));
            }
            case KATANA -> paintKatana(b, k);
        }
    }

    private static void paintKatana(WorldBatch b, float k) {
        Blade bl = KATANA_BLADE;
        strip(b, bl, bl.lo(), bl.hi(), 1, 0, fade(0xFFD6DEE8, k));

        // Sống dao: dải tối dọc mép dưới
        double[] spine = new double[bl.hi().length];
        for (int i = 0; i < spine.length; i++) {
            spine[i] = Math.min(bl.hi()[i], 0.0055);
        }
        strip(b, bl, bl.lo(), spine, 1, -0.001, fade(0xFF7D8896, k));

        // Tsuba: vòng chắn tay nằm vuông góc với lưỡi ở chân lưỡi
        WorldBatch.V tsuba = at(0, 0.014, 0);
        ring(b, tsuba, 0.028, 0.046, 24, fade(0xFF2A2A30, k));
        ring(b, tsuba, 0.046, 0.052, 24, fade(0xFFD4A02A, k));

        // Tsuka: tay cầm quấn chỉ, các đoạn xen kẽ hai màu
        for (int i = 0; i < 8; i++) {
            double u0 = -0.20 + 0.025 * i;
            int col = i % 2 == 0 ? 0xFF1A1A1E : 0xFF3B2F2A;
            box(b, u0, u0 + 0.025, 0.002, 0.026, -0.009, 0.009, fade(col, k));
        }
    }

    /** Lớp ánh tím khi vũ khí có hiệu ứng phù văn */
    private static void glint(WorldBatch b, Tier t, double pulse) {
        Blade bl = bladeOf(t);
        int col = WorldBatch.withAlpha(0xFFA25CFF, (float) (0.45 * pulse));
        strip(b, bl, bl.lo(), bl.hi(), 1, -0.002, col);
        if (t == Tier.GOLD) strip(b, bl, bl.lo(), bl.hi(), -1, -0.002, col);
    }

    /** Radiance: viền sáng đậm ở mép lưỡi và quầng mờ rộng hơn, màu lấy từ bảng màu của tính năng */
    private static void radiance(WorldBatch b, double pulse) {
        int base = ElaraConfig.color(Feature.WEAPON_3D);
        if ((base >>> 24) == 0) base = 0xFF7FE8FF;
        Blade bl = KATANA_BLADE;
        double[] hi = bl.hi();
        double[] glowLo = new double[hi.length];
        double[] glowHi = new double[hi.length];
        double[] coreLo = new double[hi.length];
        double[] coreHi = new double[hi.length];
        for (int i = 0; i < hi.length; i++) {
            glowLo[i] = hi[i] - 0.004;
            glowHi[i] = hi[i] + 0.014;
            coreLo[i] = hi[i] - 0.002;
            coreHi[i] = hi[i] + 0.004;
        }
        strip(b, bl, glowLo, glowHi, 1, -0.003, WorldBatch.withAlpha(base, (float) (0.30 * pulse)));
        strip(b, bl, coreLo, coreHi, 1, -0.004, WorldBatch.withAlpha(base, (float) (0.95 * pulse)));
    }

    // ---------- Hình học cơ bản ----------

    private static int fade(int argb, float k) {
        return WorldBatch.withAlpha(argb, k);
    }

    /** Điểm trong khung vật cầm: u dọc lưỡi, v ngang lưỡi, z theo pháp tuyến mặt lưỡi */
    private static WorldBatch.V at(double u, double v, double z) {
        return DIR.mul(u).add(SIDE.mul(v)).add(NORM.mul(z));
    }

    /** Điểm trên vòng tròn tâm c, nằm trong mặt phẳng (SIDE, NORM) */
    private static WorldBatch.V on(WorldBatch.V c, double r, double ang) {
        return c.add(SIDE.mul(r * Math.cos(ang))).add(NORM.mul(r * Math.sin(ang)));
    }

    private static double bend(Blade bl, double u) {
        double t = Math.min(1.0, Math.max(0.0, u / bl.len()));
        return bl.c0() + bl.a() * Math.pow(t, bl.p());
    }

    /** Dải lưỡi: nối các mặt cắt dọc theo chiều dài. sgn = -1 vẽ bản đối xứng qua mặt phẳng giữa. */
    private static void strip(WorldBatch b, Blade bl, double[] lo, double[] hi, double sgn, double z, int argb) {
        double[] u = bl.u();
        for (int i = 0; i + 1 < u.length; i++) {
            double b0 = bend(bl, u[i]);
            double b1 = bend(bl, u[i + 1]);
            b.quad(at(u[i], sgn * (lo[i] + b0), z), at(u[i], sgn * (hi[i] + b0), z),
                    at(u[i + 1], sgn * (hi[i + 1] + b1), z), at(u[i + 1], sgn * (lo[i + 1] + b1), z), argb);
        }
    }

    /** Hộp chữ nhật: u từ u0 đến u1, v từ v0 đến v1, z từ z0 đến z1 */
    private static void box(WorldBatch b, double u0, double u1, double v0, double v1, double z0, double z1,
                            int argb) {
        double[] us = {u0, u1};
        double[] vs = {v0, v1};
        double[] zs = {z0, z1};
        WorldBatch.V[][][] c = new WorldBatch.V[2][2][2];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 2; k++) {
                    c[i][j][k] = at(us[i], vs[j], zs[k]);
                }
            }
        }
        b.quad(c[0][0][0], c[0][1][0], c[0][1][1], c[0][0][1], argb);
        b.quad(c[1][0][0], c[1][1][0], c[1][1][1], c[1][0][1], argb);
        b.quad(c[0][0][0], c[1][0][0], c[1][0][1], c[0][0][1], argb);
        b.quad(c[0][1][0], c[1][1][0], c[1][1][1], c[0][1][1], argb);
        b.quad(c[0][0][0], c[1][0][0], c[1][1][0], c[0][1][0], argb);
        b.quad(c[0][0][1], c[1][0][1], c[1][1][1], c[0][1][1], argb);
    }

    /** Tay cầm: hộp dài từ u0 đến chân lưỡi, dày 0.014 */
    private static void handle(WorldBatch b, double u0, double v0, double v1, int argb) {
        box(b, u0, 0, v0, v1, -0.007, 0.007, argb);
    }

    /** Vòng phẳng (vành hoặc vòng ngón tay) nằm trong mặt phẳng (SIDE, NORM) */
    private static void ring(WorldBatch b, WorldBatch.V c, double r0, double r1, int seg, int argb) {
        for (int i = 0; i < seg; i++) {
            double a0 = 2 * Math.PI * i / seg;
            double a1 = 2 * Math.PI * (i + 1) / seg;
            b.quad(on(c, r0, a0), on(c, r1, a0), on(c, r1, a1), on(c, r0, a1), argb);
        }
    }

    private static WorldBatch.V unit(WorldBatch.V v) {
        double len = Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
        return v.mul(1.0 / len);
    }

    private static double dot(WorldBatch.V a, WorldBatch.V b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }

    private static WorldBatch.V cross(WorldBatch.V a, WorldBatch.V b) {
        return new WorldBatch.V(a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x());
    }
}
