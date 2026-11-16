package com.elara.client.fx;

import com.elara.client.core.Guard;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Warmup;
import com.elara.client.core.Feature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Vũ khí 3D: thay kiếm gốc bằng mô hình vẽ bằng WorldBatch.
 *
 * Kiểu 0 (Dao CS2) theo cấp kiếm: Gut (gỗ), Flip (đá), M9 (sắt), Butterfly (vàng),
 * Karambit (kim cương), Skeleton (netherite). Kiểu 1: Katana có tsuba và viền Radiance.
 *
 * Mỗi dáng được viết trong hệ tọa độ riêng: gốc là chân lưỡi (vị trí tay cầm), DIR là hướng lưỡi,
 * SIDE là ngang lưỡi, NORM là pháp tuyến mặt lưỡi. Dao CS2 được phóng to KNIFE_SCALE lần.
 * Góc nhìn thứ nhất vẽ trong khung ma trận của game. Góc nhìn thứ ba đổi điểm sang thế giới
 * theo tay của người chơi local, và chỉ vẽ khi mixin xác nhận đúng người chơi đó.
 */
public final class Weapon3D {
    public static final int STYLES = 2;
    private static final int STYLE_KATANA = 1;

    /** Dao CS2 phóng to 2.6 lần so với mô hình gốc */
    private static final double KNIFE_SCALE = 2.6;
    /** Nửa độ dày lưỡi dao và katana, đơn vị gốc */
    private static final double KNIFE_HALF = 0.0075;
    private static final double KATANA_HALF = 0.0035;
    /** Karambit cầm ngược, theo hướng tay phải (độ). Tay trái đổi dấu. */
    private static final float REVERSE_ARM_Y = -25f;
    private static final float REVERSE_ARM_Z = 75f;
    /** Góc xoay thêm của thân dao quanh cán, để lưỡi cong hướng sang trái */
    private static final float REVERSE_KNIFE_Z = 90f;
    /** Mốc từ mixin chỉ có hiệu lực trong khoảng này, tránh vẽ lặp sang khung hình sau */
    private static final long THIRD_PERSON_MAX_AGE_NS = 60_000_000L;

    /** Vẽ lỗi một lần thì tắt tới khi khởi động lại, để không báo lỗi mỗi khung hình */
    private static boolean broken = false;
    private static volatile long thirdPersonStamp = 0L;

    private enum Tier { WOOD, STONE, IRON, GOLD, DIAMOND, NETHERITE, KATANA }

    /** Một lưỡi: mặt cắt theo chiều dài u, mép dưới lo, mép trên hi, và độ cong c0 + a·t^p */
    private record Blade(double[] u, double[] lo, double[] hi, double c0, double a, double p) {
        double len() {
            return u[u.length - 1];
        }
    }

    /** Đổi điểm local sang thế giới: o + ax·x + ay·y + az·z */
    private record Pose(WorldBatch.V o, WorldBatch.V ax, WorldBatch.V ay, WorldBatch.V az) {}

    /** Bút vẽ: nhận điểm local, phóng to, đổi hệ tọa độ nếu có pose, rồi đưa vào WorldBatch */
    private static final class Canvas {
        private final WorldBatch batch;
        private final Pose pose;
        private final double s;
        private final float k;
        /** Trục và góc xoay ở hệ local (múa khi rảnh), null nếu không xoay */
        private WorldBatch.V rotAxis;
        private double rotCos = 1;
        private double rotSin = 0;

        Canvas(WorldBatch batch, Pose pose, double s, float k) {
            this.batch = batch;
            this.pose = pose;
            this.s = s;
            this.k = k;
        }

        /** Xoay mọi điểm quanh trục local đi qua gốc. Trả về chính nó để nối chuỗi. */
        Canvas spin(WorldBatch.V axis, double deg) {
            if (Math.abs(deg) < 1e-6) {
                rotAxis = null;
                return this;
            }
            double r = Math.toRadians(deg);
            rotAxis = axis;
            rotCos = Math.cos(r);
            rotSin = Math.sin(r);
            return this;
        }

        private WorldBatch.V map(WorldBatch.V p) {
            WorldBatch.V q = p.mul(s);
            if (rotAxis != null) q = rodrigues(q, rotAxis, rotCos, rotSin);
            if (pose == null) return q;
            return pose.o().add(pose.ax().mul(q.x())).add(pose.ay().mul(q.y())).add(pose.az().mul(q.z()));
        }

        void tri(WorldBatch.V a, WorldBatch.V b, WorldBatch.V c, int argb) {
            batch.tri(map(a), map(b), map(c), WorldBatch.withAlpha(argb, k));
        }

        void quad(WorldBatch.V a, WorldBatch.V b, WorldBatch.V c, WorldBatch.V d, int argb) {
            batch.quad(map(a), map(b), map(c), map(d), WorldBatch.withAlpha(argb, k));
        }
    }

    // Lưỡi dao CS2. Đơn vị gốc, sẽ nhân KNIFE_SCALE khi vẽ.
    private static final Blade GUT_BLADE = new Blade(
            new double[]{0, 0.05, 0.15, 0.25, 0.31, 0.34},
            new double[]{0, 0, 0, 0, -0.010, -0.028},
            new double[]{0.040, 0.050, 0.052, 0.042, 0.020, 0.0}, 0, 0, 1);
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
            new double[]{-0.018, -0.018, -0.012, 0.0},
            new double[]{0.018, 0.018, 0.012, 0.0}, 0, 0, 1);
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

    // Hệ trục lưỡi: DIR hướng lưỡi, SIDE ngang lưỡi, NORM pháp tuyến mặt lưỡi
    private static final WorldBatch.V X_AXIS = new WorldBatch.V(1, 0, 0);
    private static final WorldBatch.V DIR = unit(new WorldBatch.V(-0.30, 0.55, -0.78));
    private static final WorldBatch.V SIDE = unit(X_AXIS.sub(DIR.mul(dot(X_AXIS, DIR))));
    private static final WorldBatch.V NORM = unit(cross(DIR, SIDE));

    private Weapon3D() {}

    // ---------- Góc nhìn thứ nhất ----------

    /**
     * Vẽ vũ khí 3D trong khung tay cầm, kèm cánh tay người chơi. Trả về true khi đã vẽ,
     * khi đó mixin hủy bản vẽ gốc. Hàm tự cân bằng push/pop ma trận, kể cả khi lỗi.
     */
    public static boolean drawFirstPerson(MatrixStack m, AbstractClientPlayerEntity player, ItemStack stack,
                                          float swing, float equip) {
        // Sau khi vào map, vài tick đầu để vật cầm gốc của game (xem Warmup)
        if (!Warmup.ready()) return false;
        if (!active(stack)) return false;
        int style = style();
        float size = size();
        float side = player.getMainArm() == Arm.LEFT ? -1f : 1f;
        Tier tier = tierOf(stack, style);
        boolean glint = stack.hasGlint();
        float spin = KnifeAnim.spinDegrees();
        double pulse = pulse();
        double s = scaleOf(tier) * size;
        float[] idle = KnifeAnim.idlePose();
        double flip = idleDegrees(tier, idle[0]) * idle[1];
        WorldBatch.V axis = idleAxis(tier);
        boolean flourishing = Math.abs(flip) > 0.5;

        m.push();
        try {
            // Vị trí tay cầm trong khung nhìn thứ nhất, theo applyEquipOffset của game
            // Gần tâm hơn một chút và thấp hơn: cán nằm ở góc dưới phải, lưỡi chéo vào giữa màn hình
            m.translate(0.50f * side, -0.20f - equip * 0.6f, -0.66f);
            // Giật lùi nhẹ khi chém: đẩy tay về phía trước theo nhịp vung của tay
            m.translate(0f, 0f, -0.05f * (float) Math.sin(Math.sqrt(swing) * Math.PI));
            applySwing(m, side, swing);

            // Karambit (tier DIAMOND, tức diamond_sword): cầm ngược.
            // Cánh tay xoay ngang từ cạnh phải màn hình vào giữa, còn dao xoay thêm để lưỡi cong hướng sang trái.
            // Tay trái đổi dấu góc (side), nên hai bên đối xứng.
            boolean reverse = tier == Tier.DIAMOND;
            m.push();
            try {
                if (reverse) {
                    m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * REVERSE_ARM_Y));
                    m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * REVERSE_ARM_Z));
                }
                // Cánh tay vẽ trước, vũ khí vẽ sau để lưỡi không bị tay che
                PlayerArm.draw(m, side);
            } finally {
                m.pop();
            }
            if (reverse) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * REVERSE_KNIFE_Z));

            // Vệt mờ khi xoay (Inspect hoặc múa khi rảnh): ba bản sao lùi sau, càng xa càng mờ
            if (spin != 0f || flourishing) {
                for (int k = 3; k >= 1; k--) {
                    m.push();
                    try {
                        if (spin != 0f) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin - 16f * k));
                        if (flourishing) m.multiply(quat(axis, flip - 12.0 * k * Math.signum(flip)));
                        WorldBatch ghost = new WorldBatch(m.peek().getPositionMatrix(), 0, 0, 0, false);
                        paintSolid(new Canvas(ghost, null, s, 0.35f / k), tier);
                        ghost.flush();
                    } finally {
                        m.pop();
                    }
                }
            }

            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
            if (flourishing) m.multiply(quat(axis, flip));
            Matrix4f mat = m.peek().getPositionMatrix();
            WorldBatch solid = new WorldBatch(mat, 0, 0, 0, false);
            paintSolid(new Canvas(solid, null, s, 1f), tier);
            solid.flush();
            drawAdditive(mat, 0, 0, 0, null, s, tier, glint, pulse, axis, 0);
            return true;
        } catch (Throwable ex) {
            Guard.rethrowIfFatal(ex);
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

    // ---------- Góc nhìn thứ ba ----------

    /** Đang vẽ người chơi local. Được đặt ngay trước khi vẽ mỗi thực thể (EntityRenderDispatcherMixin). */
    private static volatile boolean localRendering;

    public static void setRenderingLocal(boolean on) {
        localRendering = on;
    }

    public static boolean isRenderingLocalPlayer() {
        return localRendering;
    }

    /**
     * Trả về true khi vật cầm gốc của người chơi local nên được thay bằng dao 3D: góc nhìn thứ ba,
     * đang cầm kiếm ở tay chính, tay phụ trống, không mở màn hình nào. Người chơi local được nhận biết
     * nhờ setRenderingLocal, nên không cần đối chiếu vị trí (vị trí cũ làm nhầm khi hai người đứng sát).
     */
    public static boolean replacesThirdPerson(MinecraftClient client, MatrixStack m) {
        if (broken || !ElaraConfig.isOn(Feature.WEAPON_3D) || !Warmup.heavy()) return false;
        if (client.player == null || client.options == null) return false;
        if (client.currentScreen != null) return false;
        if (client.options.getPerspective().isFirstPerson()) return false;
        if (!(client.player.getMainHandStack().getItem() instanceof SwordItem)) return false;
        return client.player.getOffHandStack().isEmpty();
    }

    /** Mixin đánh dấu khung hình này cần vẽ vũ khí 3D thay cho bản gốc */
    public static void markThirdPerson() {
        thirdPersonStamp = System.nanoTime();
    }

    /** Gọi trong lượt vẽ hiệu ứng thế giới, sau khi các thực thể đã vẽ xong */
    public static void drawThirdPersonIfMarked(Vec3d cam) {
        long stamp = thirdPersonStamp;
        if (stamp == 0L) return;
        thirdPersonStamp = 0L;
        if (broken || System.nanoTime() - stamp > THIRD_PERSON_MAX_AGE_NS) return;

        MinecraftClient client = MinecraftClient.getInstance();
        AbstractClientPlayerEntity player = client.player;
        if (player == null) return;
        ItemStack stack = player.getMainHandStack();
        if (!active(stack)) return;

        try {
            int style = style();
            float size = size();
            Tier tier = tierOf(stack, style);
            boolean glint = stack.hasGlint();
            float side = player.getMainArm() == Arm.LEFT ? -1f : 1f;

            // Hướng nhìn theo thân người: fwd là phía trước, right là bên phải của người
            double yaw = Math.toRadians(player.getYaw());
            WorldBatch.V fwd = new WorldBatch.V(-Math.sin(yaw), 0, Math.cos(yaw));
            WorldBatch.V right = new WorldBatch.V(-Math.cos(yaw), 0, -Math.sin(yaw));
            WorldBatch.V up = new WorldBatch.V(0, 1, 0);
            WorldBatch.V feet = new WorldBatch.V(player.getX(), player.getY(), player.getZ());
            WorldBatch.V hand = feet.add(right.mul(0.40 * side)).add(up.mul(1.05)).add(fwd.mul(0.30));
            // Trục z âm của vật cầm hướng về phía trước, nên az = -fwd
            Pose pose = new Pose(hand, right, up, fwd.mul(-1));
            double s = scaleOf(tier) * size;

            // Múa khi rảnh cũng hiện ở góc nhìn thứ ba, xoay các điểm local trước khi đổi sang thế giới
            float[] idle = KnifeAnim.idlePose();
            double flip = idleDegrees(tier, idle[0]) * idle[1];
            WorldBatch.V axis = idleAxis(tier);

            Vec3d c = cam;
            WorldBatch solid = new WorldBatch(new Matrix4f(), c.x, c.y, c.z, false);
            paintSolid(new Canvas(solid, pose, s, 1f).spin(axis, flip), tier);
            solid.flush();
            drawAdditive(new Matrix4f(), c.x, c.y, c.z, pose, s, tier, glint, pulse(), axis, flip);
        } catch (Throwable ex) {
            Guard.rethrowIfFatal(ex);
            broken = true;
            System.err.println("[Elara] Tắt Vũ khí 3D ở góc nhìn thứ ba do lỗi: " + ex);
        }
    }

    // ---------- Múa khi rảnh ----------

    /** Trục xoay khi múa của từng dáng, trong hệ local của lưỡi (DIR, SIDE, NORM) */
    private static WorldBatch.V idleAxis(Tier t) {
        return switch (t) {
            case IRON, DIAMOND -> DIR;
            case WOOD, STONE -> SIDE;
            case GOLD, NETHERITE, KATANA -> NORM;
        };
    }

    /**
     * Góc múa theo pha p từ 0 đến 1. Lật hoặc xoay tròn cho các dao có cán và butterfly, lắc cổ tay
     * cho gut, skeleton và katana. Ở p = 1 mọi góc về 0 hoặc 360 độ, nên quay về tư thế cầm không bị nhảy.
     */
    private static double idleDegrees(Tier t, double p) {
        double ease = p * p * (3 - 2 * p);
        double wave = Math.sin(2 * Math.PI * p);
        return switch (t) {
            case WOOD -> 40 * wave;
            case STONE, IRON, GOLD, DIAMOND -> 360 * ease;
            case NETHERITE -> 60 * wave;
            case KATANA -> 35 * wave;
        };
    }

    private static Quaternionf quat(WorldBatch.V axis, double deg) {
        return new Quaternionf().rotationAxis((float) Math.toRadians(deg),
                (float) axis.x(), (float) axis.y(), (float) axis.z());
    }

    /** Xoay v quanh trục đơn vị k với cos c và sin s: v·c + (k×v)·s + k(k·v)(1−c) */
    private static WorldBatch.V rodrigues(WorldBatch.V v, WorldBatch.V k, double c, double s) {
        return v.mul(c).add(cross(k, v).mul(s)).add(k.mul(dot(k, v) * (1 - c)));
    }

    // ---------- Chung ----------

    private static boolean active(ItemStack stack) {
        return !broken && ElaraConfig.isOn(Feature.WEAPON_3D) && stack.getItem() instanceof SwordItem;
    }

    private static int style() {
        return Math.max(0, Math.min(STYLES - 1, ElaraConfig.layout(Feature.WEAPON_3D).get("style", 0)));
    }

    private static float size() {
        return Math.max(0.5f, ElaraConfig.layout(Feature.WEAPON_3D).scalePct / 100f);
    }

    private static double pulse() {
        return 0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 1000.0 * 4.0);
    }

    private static double scaleOf(Tier t) {
        return t == Tier.KATANA ? 1.0 : KNIFE_SCALE;
    }

    /** Lớp cộng sáng: ánh tím khi có phù văn, viền Radiance cho katana */
    private static void drawAdditive(Matrix4f mat, double cx, double cy, double cz, Pose pose, double s,
                                     Tier tier, boolean glint, double pulse, WorldBatch.V axis, double deg) {
        if (!glint && tier != Tier.KATANA) return;
        WorldBatch add = new WorldBatch(mat, cx, cy, cz, true);
        Canvas c = new Canvas(add, pose, s, 1f).spin(axis, deg);
        if (glint) glint(c, tier, pulse);
        if (tier == Tier.KATANA) radiance(c, pulse);
        add.flush();
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

    private static double halfOf(Tier t) {
        return switch (t) {
            case KATANA -> KATANA_HALF;
            case WOOD -> KNIFE_HALF * 2;
            case GOLD -> KNIFE_HALF * 0.7;
            default -> KNIFE_HALF;
        };
    }

    // ---------- Các dáng vẽ ----------

    private static void paintSolid(Canvas c, Tier t) {
        switch (t) {
            case WOOD -> {
                // Gut có sống dày gấp đôi các dao khác
                blade(c, GUT_BLADE, KNIFE_HALF * 2, 0xFFB9C3CF);
                // Cán gỗ, đầu cán bo nhẹ bằng hai khối lệch
                box(c, -0.12, 0.0, 0.0, 0.036, -0.009, 0.009, 0xFF8B5A2B);
                box(c, -0.13, -0.12, 0.004, 0.032, -0.008, 0.008, 0xFF6B4321);
            }
            case STONE -> {
                blade(c, FLIP_BLADE, KNIFE_HALF, 0xFF9AA4B0);
                // Vòng flipper quanh trục xoay ở sống dao
                torus(c, L(-0.035, -0.014, 0), 0.030, 0.009, 20, 8, 0xFF5C6673);
                // Cán gập: hai nửa tách nhau bởi khe, có chốt xoay ở giữa
                box(c, -0.12, 0.0, 0.046, 0.062, -0.010, 0.010, 0xFF2B3138);
                box(c, -0.12, 0.0, -0.018, 0.0, -0.010, 0.010, 0xFF2B3138);
                box(c, -0.022, -0.012, -0.018, 0.062, -0.012, 0.012, 0xFFB0B8C2);
            }
            case IRON -> {
                blade(c, M9_BLADE, KNIFE_HALF, 0xFF5F6E80);
                // Lưng răng cưa sâu dọc sống dao, mỗi răng có hai mặt
                for (int i = 0; i < 9; i++) {
                    double x0 = 0.07 + 0.025 * i;
                    c.tri(L(x0, 0, KNIFE_HALF), L(x0 + 0.025, 0, KNIFE_HALF), L(x0 + 0.025, 0.016, KNIFE_HALF),
                            0xFF46505E);
                    c.tri(L(x0, 0, -KNIFE_HALF), L(x0 + 0.025, 0, -KNIFE_HALF), L(x0 + 0.025, 0.016, -KNIFE_HALF),
                            0xFF46505E);
                }
                // Chắn tay dày, có lỗ tròn ở mặt trước
                box(c, -0.012, 0.010, -0.052, 0.022, -0.012, 0.012, 0xFF1E1E22);
                diamondHole(c, -0.001, -0.016, 0.0125, 0.006, 0.010, 0xFF0B0B0D);
                // Cán có rãnh: các rãnh tối nhô ra khỏi mặt cán
                box(c, -0.13, -0.012, -0.030, 0.010, -0.007, 0.007, 0xFF1E1E22);
                for (int i = 0; i < 5; i++) {
                    double u0 = -0.125 + 0.022 * i;
                    box(c, u0, u0 + 0.012, -0.030, 0.010, -0.0085, 0.0085, 0xFF0C0C0E);
                }
            }
            case GOLD -> {
                // Lưỡi nằm giữa, hai cán kẹp hai bên như Butterfly thật
                blade(c, BUTTERFLY_BLADE, KNIFE_HALF * 0.7, 0xFFAEB8C4);
                box(c, -0.14, 0.02, 0.020, 0.046, -0.010, 0.010, 0xFFD9A62E);
                box(c, -0.14, 0.02, -0.046, -0.020, -0.010, 0.010, 0xFFD9A62E);
                // Chốt xoay xuyên qua hai cán
                box(c, -0.058, -0.048, -0.046, 0.046, -0.005, 0.005, 0xFFB07F1E);
            }
            case DIAMOND -> {
                blade(c, KARAMBIT_BLADE, KNIFE_HALF, 0xFF7FE3F0);
                // Vòng xỏ ngón to, nối với cán bằng một thanh
                torus(c, L(-0.075, -0.050, 0), 0.042, 0.013, 22, 8, 0xFF3E5566);
                box(c, -0.10, -0.06, -0.050, -0.004, -0.008, 0.008, 0xFF1F2A33);
                // Cán công thái học
                box(c, -0.12, 0.0, -0.004, 0.030, -0.010, 0.010, 0xFF1F2A33);
            }
            case NETHERITE -> {
                blade(c, SKELETON_BLADE, KNIFE_HALF, 0xFF3B3340);
                // Hai lỗ khoét hình thoi ở thân
                diamondHole(c, 0.12, 0.0, KNIFE_HALF + 0.001, 0.028, 0.012, 0xFF0B0A0D);
                diamondHole(c, 0.25, 0.0, KNIFE_HALF + 0.001, 0.022, 0.009, 0xFF0B0A0D);
                // Cán xương
                box(c, -0.12, 0.0, -0.012, 0.012, -0.008, 0.008, 0xFFE8E2D0);
                box(c, -0.13, -0.11, -0.016, 0.016, -0.009, 0.009, 0xFFE8E2D0);
            }
            case KATANA -> paintKatana(c);
        }
    }

    private static void paintKatana(Canvas c) {
        blade(c, KATANA_BLADE, KATANA_HALF, 0xFFD6DEE8);

        // Sống dao: dải tối dọc mép dưới, cả hai mặt
        double[] spine = new double[KATANA_BLADE.hi().length];
        for (int i = 0; i < spine.length; i++) {
            spine[i] = Math.min(KATANA_BLADE.hi()[i], 0.0055);
        }
        strip(c, KATANA_BLADE, KATANA_BLADE.lo(), spine, 1, KATANA_HALF + 0.0005, 0xFF7D8896);
        strip(c, KATANA_BLADE, KATANA_BLADE.lo(), spine, 1, -KATANA_HALF - 0.0005, 0xFF7D8896);

        // Tsuba: vòng chắn tay vuông góc với lưỡi, có viền vàng
        annulus(c, L(0.003, 0.014, 0), 0.028, 0.046, 24, 0xFF2A2A30);
        annulus(c, L(-0.003, 0.014, 0), 0.028, 0.046, 24, 0xFF2A2A30);
        annulus(c, L(0.003, 0.014, 0), 0.046, 0.052, 24, 0xFFD4A02A);
        annulus(c, L(-0.003, 0.014, 0), 0.046, 0.052, 24, 0xFFD4A02A);

        // Tsuka: tay cầm quấn chỉ, các đoạn xen kẽ hai màu
        for (int i = 0; i < 8; i++) {
            double u0 = -0.20 + 0.025 * i;
            int col = i % 2 == 0 ? 0xFF1A1A1E : 0xFF3B2F2A;
            box(c, u0, u0 + 0.025, 0.002, 0.026, -0.009, 0.009, col);
        }
    }

    private static void glint(Canvas c, Tier t, double pulse) {
        Blade bl = bladeOf(t);
        int col = WorldBatch.withAlpha(0xFFA25CFF, (float) (0.45 * pulse));
        strip(c, bl, bl.lo(), bl.hi(), 1, halfOf(t) + 0.0006, col);
    }

    /** Radiance: viền sáng đậm ở mép lưỡi và quầng mờ rộng hơn, màu lấy từ bảng màu của tính năng */
    private static void radiance(Canvas c, double pulse) {
        int base = ElaraConfig.color(Feature.WEAPON_3D);
        if ((base >>> 24) == 0) base = 0xFF7FE8FF;
        double[] hi = KATANA_BLADE.hi();
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
        double z = KATANA_HALF + 0.0015;
        strip(c, KATANA_BLADE, glowLo, glowHi, 1, z, WorldBatch.withAlpha(base, (float) (0.30 * pulse)));
        strip(c, KATANA_BLADE, coreLo, coreHi, 1, z + 0.0005, WorldBatch.withAlpha(base, (float) (0.95 * pulse)));
    }

    // ---------- Hình học ----------

    /** Điểm local: u dọc lưỡi, v ngang lưỡi, z theo pháp tuyến mặt lưỡi */
    private static WorldBatch.V L(double u, double v, double z) {
        return DIR.mul(u).add(SIDE.mul(v)).add(NORM.mul(z));
    }

    private static WorldBatch.V onPlane(WorldBatch.V center, double r, double ang) {
        return center.add(SIDE.mul(r * Math.cos(ang))).add(NORM.mul(r * Math.sin(ang)));
    }

    private static double bend(Blade bl, double u) {
        double t = Math.min(1.0, Math.max(0.0, u / bl.len()));
        return bl.c0() + bl.a() * Math.pow(t, bl.p());
    }

    /** Dải lưỡi: nối các mặt cắt dọc theo chiều dài ở độ sâu z. sgn = -1 vẽ bản đối xứng. */
    private static void strip(Canvas c, Blade bl, double[] lo, double[] hi, double sgn, double z, int argb) {
        double[] u = bl.u();
        for (int i = 0; i + 1 < u.length; i++) {
            double b0 = bend(bl, u[i]);
            double b1 = bend(bl, u[i + 1]);
            c.quad(L(u[i], sgn * (lo[i] + b0), z), L(u[i], sgn * (hi[i] + b0), z),
                    L(u[i + 1], sgn * (hi[i + 1] + b1), z), L(u[i + 1], sgn * (lo[i + 1] + b1), z), argb);
        }
    }

    /** Cạnh của lưỡi nối hai mặt, từ độ sâu zA đến zB */
    private static void edge(Canvas c, Blade bl, double[] arr, double zA, double zB, int argb) {
        double[] u = bl.u();
        for (int i = 0; i + 1 < u.length; i++) {
            double b0 = bend(bl, u[i]);
            double b1 = bend(bl, u[i + 1]);
            c.quad(L(u[i], arr[i] + b0, zA), L(u[i + 1], arr[i + 1] + b1, zA),
                    L(u[i + 1], arr[i + 1] + b1, zB), L(u[i], arr[i] + b0, zB), argb);
        }
    }

    /** Lưỡi có độ dày: hai mặt phẳng cộng bốn cạnh tối hơn, để thấy khối 3D */
    private static void blade(Canvas c, Blade bl, double half, int argb) {
        int wall = darken(argb, 0.55f);
        strip(c, bl, bl.lo(), bl.hi(), 1, half, argb);
        strip(c, bl, bl.lo(), bl.hi(), 1, -half, argb);
        edge(c, bl, bl.lo(), half, -half, wall);
        edge(c, bl, bl.hi(), half, -half, wall);
        // Vát cạnh sắc: dải sáng dọc mép lưỡi trên cả hai mặt, tạo khác biệt mặt sáng và mặt tối
        int bright = lighten(argb, 0.35f);
        double[] cut = new double[bl.hi().length];
        for (int i = 0; i < cut.length; i++) cut[i] = bl.hi()[i] - 0.0035;
        strip(c, bl, cut, bl.hi(), 1, half + 0.0004, bright);
        strip(c, bl, cut, bl.hi(), 1, -half - 0.0004, bright);
    }

    /** Khối hộp: mặt trước và sau theo z, bốn mặt bên tối hơn */
    private static void box(Canvas c, double u0, double u1, double v0, double v1, double z0, double z1, int argb) {
        double[] us = {u0, u1};
        double[] vs = {v0, v1};
        double[] zs = {z0, z1};
        WorldBatch.V[][][] p = new WorldBatch.V[2][2][2];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 2; k++) {
                    p[i][j][k] = L(us[i], vs[j], zs[k]);
                }
            }
        }
        int wall = darken(argb, 0.6f);
        c.quad(p[0][0][0], p[0][1][0], p[0][1][1], p[0][0][1], wall);
        c.quad(p[1][0][0], p[1][1][0], p[1][1][1], p[1][0][1], wall);
        c.quad(p[0][0][0], p[1][0][0], p[1][0][1], p[0][0][1], wall);
        c.quad(p[0][1][0], p[1][1][0], p[1][1][1], p[0][1][1], wall);
        c.quad(p[0][0][0], p[1][0][0], p[1][1][0], p[0][1][0], argb);
        c.quad(p[0][0][1], p[1][0][1], p[1][1][1], p[0][1][1], argb);
    }

    /** Hình thoi khoét rỗng trên một mặt, tâm (u, v) ở độ sâu z */
    private static void diamondHole(Canvas c, double u, double v, double z, double rx, double ry, int argb) {
        c.tri(L(u - rx, v, z), L(u, v + ry, z), L(u + rx, v, z), argb);
        c.tri(L(u - rx, v, z), L(u + rx, v, z), L(u, v - ry, z), argb);
    }

    /** Vòng phẳng nằm trong mặt (SIDE, NORM) */
    private static void annulus(Canvas c, WorldBatch.V center, double r0, double r1, int seg, int argb) {
        for (int i = 0; i < seg; i++) {
            double a0 = 2 * Math.PI * i / seg;
            double a1 = 2 * Math.PI * (i + 1) / seg;
            c.quad(onPlane(center, r0, a0), onPlane(center, r1, a0), onPlane(center, r1, a1), onPlane(center, r0, a1),
                    argb);
        }
    }

    /** Vòng có độ dày (torus) nằm trong mặt lưỡi (DIR, SIDE), ống vòng theo NORM, như vòng xỏ ngón */
    private static void torus(Canvas c, WorldBatch.V center, double major, double minor, int seg, int tube,
                              int argb) {
        for (int i = 0; i < seg; i++) {
            double a0 = 2 * Math.PI * i / seg;
            double a1 = 2 * Math.PI * (i + 1) / seg;
            for (int j = 0; j < tube; j++) {
                double p0 = 2 * Math.PI * j / tube;
                double p1 = 2 * Math.PI * (j + 1) / tube;
                c.quad(torusPt(center, major, minor, a0, p0), torusPt(center, major, minor, a1, p0),
                        torusPt(center, major, minor, a1, p1), torusPt(center, major, minor, a0, p1), argb);
            }
        }
    }

    private static WorldBatch.V torusPt(WorldBatch.V center, double major, double minor, double a, double p) {
        double ring = major + minor * Math.cos(p);
        return center.add(DIR.mul(Math.cos(a) * ring)).add(SIDE.mul(Math.sin(a) * ring))
                .add(NORM.mul(minor * Math.sin(p)));
    }

    /** Làm sáng màu về phía trắng, f từ 0 đến 1 */
    private static int lighten(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) + (255 - ((argb >> 16) & 0xFF)) * f);
        int g = (int) (((argb >> 8) & 0xFF) + (255 - ((argb >> 8) & 0xFF)) * f);
        int b = (int) ((argb & 0xFF) + (255 - (argb & 0xFF)) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---------- Toán vector ----------

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
