package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.core.ColorMath;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Thế giới sống động, chỉ dùng WorldBatch (tam giác và đoạn màu, không shader, không framebuffer)
 * nên chạy được trên OpenGL thuần của PojavLauncher và FCL.
 *
 * Bầu trời (9 kiểu, gồm Sakura): đổi màu nền trời qua mixin getSkyColor, và vẽ các đốm sao, tinh vân,
 * cực quang, trăng ở xa 160 block. Gợn sóng mưa: quét mặt nước và mặt đất quanh người chơi, vẽ vòng gợn
 * tối đa 150 cái, cộng vệt sáng trên đất ướt (xấp xỉ, không phải phản chiếu thật). Hạt môi trường: bụi
 * sáng, bụi sao, cánh hoa anh đào đọng trên đất vài giây, bông tuyết. Mist/Glass/Bokeh: sương sát đất,
 * tia kính lấp lánh, đốm bokeh mờ. Chroma World nhuộm màu trời qua skyColor, không vẽ thêm gì.
 */
public final class WorldFx {
    public static final int SKY_STYLES = 9;
    public static final int AMBIENCE_STYLES = 4;
    public static final int MIST_STYLES = 3;
    /** Giới hạn gợn sóng đồng thời, để giữ FPS trên điện thoại */
    public static final int RIPPLE_MAX = 150;

    /** Khoảng cách vẽ bầu trời, nằm trong tầm xa của camera */
    private static final double SKY_R = 160.0;
    /** Nửa cạnh vùng hạt quanh người chơi */
    private static final double RANGE = 9.0;
    /** Bán kính quét mặt nước và mặt đất quanh người chơi */
    private static final int SCAN_R = 12;
    private static final long SCAN_MS = 500L;
    private static final double RIPPLE_LIFE_MS = 900.0;
    /** Thời gian một cánh hoa đọng trên đất, rồi mờ dần trong 1 giây */
    private static final double LAND_S = 3.0;
    private static final double FADE_S = 1.0;

    private static final WorldBatch.V UP = new WorldBatch.V(0, 1, 0);
    /** Màu nền trời theo kiểu (chỉ lấy RGB) */
    private static final int[] SKY_COLORS = {
            0xFF0B1030, 0xFF2A0E45, 0xFFE08A52, 0xFF7DBEFF, 0xFF06152A,
            0xFF050818, 0xFF3A0A0A, 0xFF1B2D44, 0xFFF3B8D0
    };
    private static final int[] AMBIENCE_COUNT = {24, 44, 72};
    private static final int[] MIST_COUNT = {8, 16, 26};

    private static final WorldBatch.V[] STARS = new WorldBatch.V[260];
    private static final WorldBatch.V[] MILKY = new WorldBatch.V[320];

    static {
        Random r = new Random(7L);
        for (int i = 0; i < STARS.length; i++) {
            double y = 0.05 + r.nextDouble() * 0.9;
            double th = r.nextDouble() * Math.PI * 2;
            double rho = Math.sqrt(1 - y * y);
            STARS[i] = new WorldBatch.V(rho * Math.cos(th), y, rho * Math.sin(th));
        }
        for (int i = 0; i < MILKY.length; i++) {
            double t = r.nextDouble() * Math.PI * 2;
            double jitter = (r.nextDouble() - 0.5) * 0.25;
            MILKY[i] = unit(new WorldBatch.V(Math.cos(t), 0.45 + 0.25 * Math.sin(t) + jitter, Math.sin(t)));
        }
    }

    /** Hạt môi trường: vị trí, vận tốc, pha, và trạng thái đã đọng trên đất */
    private static final class Particle {
        double x, y, z, vx, vy, vz, spin, phase, landedAt;
        boolean landed;
    }

    private static final List<Particle> PARTS = new ArrayList<>();
    private static int partStyle = -1;
    /** Gợn sóng đang chạy: x, y, z, mốc thời gian (ms) */
    private static final List<double[]> RIPPLES = new ArrayList<>();
    /** Điểm mặt nước và mặt đất quét được gần đây: x, y, z */
    private static final List<double[]> WATER = new ArrayList<>();
    private static final List<double[]> GROUND = new ArrayList<>();
    private static long scanAt = 0L;
    private static long lastFrame = 0L;
    private static final Random RNG = new Random();

    private WorldFx() {}

    /** Có lớp thế giới nào đang vẽ không. Chroma World không tính ở đây vì nó đổi màu trời, không vẽ. */
    public static boolean active() {
        return ElaraConfig.isOn(Feature.WORLD_SKY) || ElaraConfig.isOn(Feature.RAIN_RIPPLE)
                || ElaraConfig.isOn(Feature.AMBIENCE) || ElaraConfig.isOn(Feature.MIST_GLASS);
    }

    /** Có đang đổi màu trời không: Bầu trời sống động hoặc Chroma World */
    public static boolean skyActive() {
        return ElaraConfig.isOn(Feature.WORLD_SKY) || ElaraConfig.isOn(Feature.CHROMA_WORLD);
    }

    /**
     * Màu nền trời sau khi áp Bầu trời sống động và Chroma World. Mixin getSkyColor gọi hàm này,
     * nên màu sương theo chân trời cũng đổi theo.
     */
    public static Vec3d skyColor(Vec3d base) {
        Vec3d out = base;
        if (ElaraConfig.isOn(Feature.WORLD_SKY)) {
            out = rgb(SKY_COLORS[style(Feature.WORLD_SKY, SKY_STYLES)]);
        }
        if (ElaraConfig.isOn(Feature.CHROMA_WORLD)) {
            int tint = ElaraConfig.color(Feature.CHROMA_WORLD);
            double strength = ElaraConfig.layout(Feature.CHROMA_WORLD).get("strength", 35) / 100.0;
            double k = strength * (((tint >>> 24) & 0xFF) / 255.0);
            Vec3d c = rgb(tint);
            out = new Vec3d(out.x + (c.x - out.x) * k, out.y + (c.y - out.y) * k, out.z + (c.z - out.z) * k);
        }
        return out;
    }

    /** Vẽ mọi lớp thế giới đang bật. Gọi trong AFTER_TRANSLUCENT, sau khi địa hình đã vẽ. */
    public static void draw(Matrix4f m, WorldBatch.V cam, MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0L ? 0.05f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        if (ElaraConfig.isOn(Feature.WORLD_SKY)) drawSky(m, cam, now);
        if (ElaraConfig.isOn(Feature.RAIN_RIPPLE)) {
            drawRain(m, cam, client, now, dt);
        } else {
            RIPPLES.clear();
        }
        if (ElaraConfig.isOn(Feature.AMBIENCE)) {
            drawAmbience(m, cam, client, now, dt, px, py, pz);
        } else {
            PARTS.clear();
            partStyle = -1;
        }
        if (ElaraConfig.isOn(Feature.MIST_GLASS)) drawMist(m, cam, now, px, py, pz);
    }

    // ---------- Bầu trời ----------

    private static void drawSky(Matrix4f m, WorldBatch.V cam, long now) {
        int style = style(Feature.WORLD_SKY, SKY_STYLES);
        double t = now / 1000.0;
        // Một batch cho cả bầu trời: WorldBatch dùng chung Tessellator, hai batch cùng lúc sẽ xung đột.
        // Sao, mây và mặt trời đều là màu có độ trong suốt nên hòa trộn thường là đủ.
        WorldBatch add = new WorldBatch(m, cam.x(), cam.y(), cam.z(), false);
        WorldBatch norm = add;
        switch (style) {
            case 0 -> stars(add, cam, t, STARS.length, 0xFFFFFFFF, 0.8);
            case 1 -> {
                stars(add, cam, t, 120, 0xFFE6D8FF, 0.8);
                for (int i = 0; i < 6; i++) {
                    blob(norm, cam, STARS[(i * 37) % STARS.length], SKY_R, 55, 0x2EB26BFF);
                }
            }
            case 2 -> {
                WorldBatch.V sun = unit(new WorldBatch.V(-0.55, 0.12, 0.83));
                blob(add, cam, sun, SKY_R, 14, 0xFFFFD27A);
                blob(add, cam, sun, SKY_R, 40, 0x66FFA050);
                for (int i = 0; i < 6; i++) {
                    WorldBatch.V d = dirAt(i * 1.05, 0.10 + 0.05 * Math.sin(i));
                    blob(norm, cam, d, SKY_R, 45, 0x55FF9A4D);
                }
            }
            case 3 -> {
                WorldBatch.V sun = unit(new WorldBatch.V(0.7, 0.18, -0.6));
                blob(add, cam, sun, SKY_R, 12, 0xFFFFF3D6);
                blob(add, cam, sun, SKY_R, 36, 0x55FFF3D6);
                stars(add, cam, t, 40, 0xFFFFFFFF, 0.5);
            }
            case 4 -> {
                stars(add, cam, t, 80, 0xFFFFFFFF, 0.6);
                for (int i = 0; i < 12; i++) ribbon(add, cam, t, i);
            }
            case 5 -> {
                stars(add, cam, t, 60, 0xFFFFFFFF, 0.5);
                for (WorldBatch.V d : MILKY) blob(add, cam, d, SKY_R, 0.9, 0x66CFE0FF);
            }
            case 6 -> {
                stars(add, cam, t, 80, 0xFFFFFFFF, 0.6);
                WorldBatch.V moon = unit(new WorldBatch.V(0.25, 0.5, -0.83));
                blob(norm, cam, moon, SKY_R, 11, 0xFFFF3B3B);
                blob(add, cam, moon, SKY_R, 28, 0x55FF2020);
            }
            case 7 -> {
                stars(add, cam, t, 120, 0xFFDDEBFF, 0.45);
                for (int i = 0; i < 4; i++) {
                    blob(norm, cam, dirAt(i * 1.6 + 0.5, 0.25 + 0.1 * i), SKY_R, 60, 0x33DDEEFF);
                }
            }
            default -> {
                // Sakura: mây hồng pastel trôi chậm quanh trời, sao lấp lánh hồng nhạt
                stars(add, cam, t, 60, 0xFFFFC2DE, 0.7);
                for (int i = 0; i < 9; i++) {
                    double az = i * 0.7 + t * 0.01 * (1 + 0.1 * i);
                    double el = 0.15 + 0.12 * (i % 3);
                    blob(norm, cam, dirAt(az, el), SKY_R, 55 + (i % 3) * 10, 0x66FFD0E0);
                }
            }
        }
        add.flush();
    }

    /** count điểm sao đầu tiên, mỗi điểm nhấp nháy theo thời gian */
    private static void stars(WorldBatch b, WorldBatch.V cam, double t, int count, int argb, double size) {
        for (int i = 0; i < count && i < STARS.length; i++) {
            double tw = 0.45 + 0.55 * Math.sin(t * 1.7 + i * 1.3);
            blob(b, cam, STARS[i], SKY_R, size, WorldBatch.withAlpha(argb, (float) tw));
        }
    }

    /** Một dải cực quang: sáu đoạn cong, màu chuyển giữa xanh lá và xanh lam */
    private static void ribbon(WorldBatch b, WorldBatch.V cam, double t, int i) {
        double th0 = i * Math.PI * 2 / 12;
        int color = mixArgb(0xCC37FFB0, 0xCC4BD8FF, (float) (0.5 + 0.5 * Math.sin(t + i)));
        WorldBatch.V prev = null;
        for (int seg = 0; seg <= 6; seg++) {
            double el = 0.25 + seg * 0.16;
            double th = th0 + 0.10 * Math.sin(t * 0.7 + i * 0.9 + seg * 0.8);
            WorldBatch.V p = cam.add(dirAt(th, el).mul(SKY_R));
            if (prev != null) b.billboardBeam(prev, p, cam, 5.0, color);
            prev = p;
        }
    }

    /** Điểm trên trời ở hướng (góc phương vị, độ cao) tính bằng radian */
    private static WorldBatch.V dirAt(double azimuth, double elevation) {
        return new WorldBatch.V(Math.cos(azimuth) * Math.cos(elevation), Math.sin(elevation),
                Math.sin(azimuth) * Math.cos(elevation));
    }

    /** Một đốm sáng hình vuông nhìn về camera, tại cam + dir·r */
    private static void blob(WorldBatch b, WorldBatch.V cam, WorldBatch.V dir, double r, double half, int argb) {
        WorldBatch.V c = cam.add(dir.mul(r));
        quadBill(b, c, cam, half, half, argb);
    }

    // ---------- Gợn sóng mưa ----------

    private static void drawRain(Matrix4f m, WorldBatch.V cam, MinecraftClient client, long now, float dt) {
        ElaraConfig.Layout l = ElaraConfig.layout(Feature.RAIN_RIPPLE);
        boolean raining = client.world.isRaining() || l.get("force", 0) == 1;
        if (!raining) {
            RIPPLES.clear();
            return;
        }
        if (now - scanAt > SCAN_MS) {
            scan(client);
            scanAt = now;
        }
        int cap = Math.max(10, Math.min(RIPPLE_MAX, l.get("count", 120)));
        int argb = ElaraConfig.color(Feature.RAIN_RIPPLE);

        // Mỗi khung sinh vài gợn tại mặt nước ngẫu nhiên, đủ để giữ khoảng cap gợn đang chạy
        if (!WATER.isEmpty()) {
            int spawn = (int) Math.ceil(cap * dt / (RIPPLE_LIFE_MS / 1000.0));
            for (int i = 0; i < spawn && RIPPLES.size() < cap; i++) {
                double[] p = WATER.get(RNG.nextInt(WATER.size()));
                RIPPLES.add(new double[] {p[0], p[1], p[2], now});
            }
        }
        RIPPLES.removeIf(r -> now - r[3] > RIPPLE_LIFE_MS);

        WorldBatch ring = new WorldBatch(m, cam.x(), cam.y(), cam.z(), false);
        for (double[] r : RIPPLES) {
            double a = (now - r[3]) / RIPPLE_LIFE_MS;
            if (a < 0 || a > 1) continue;
            double rad = 0.08 + 0.6 * a;
            double thick = 0.03 * (1 - a) + 0.012;
            ring.ring(new WorldBatch.V(r[0], r[1], r[2]), rad, thick, 12, 0,
                    WorldBatch.withAlpha(argb, (float) (0.85 * (1 - a))));
        }
        ring.flush();

        // Mặt đất ướt: vệt sáng bầu trời lướt nhẹ, mỗi khung lấy ngẫu nhiên vài ô đất quanh người chơi
        if (l.get("wet", 1) == 1 && !GROUND.isEmpty()) {
            WorldBatch sheen = new WorldBatch(m, cam.x(), cam.y(), cam.z(), true);
            double t = now / 1000.0;
            int samples = Math.min(40, GROUND.size());
            for (int i = 0; i < samples; i++) {
                double[] g = GROUND.get(RNG.nextInt(GROUND.size()));
                double pulse = 0.5 + 0.5 * Math.sin(t * 3 + g[0] * 1.3 + g[2] * 0.7);
                int c = WorldBatch.withAlpha(0xFFDDEEFF, (float) (0.04 + 0.06 * pulse));
                flat(sheen, g[0] + 0.5, g[1], g[2] + 0.5, 0.5, 0.5, 0, c);
            }
            sheen.flush();
        }
    }

    /** Quét quanh người chơi: mặt nước có không khí phía trên, và mặt đất đặc có không khí phía trên */
    private static void scan(MinecraftClient client) {
        WATER.clear();
        GROUND.clear();
        World world = client.world;
        BlockPos base = client.player.getBlockPos();
        for (int dx = -SCAN_R; dx <= SCAN_R; dx++) {
            for (int dz = -SCAN_R; dz <= SCAN_R; dz++) {
                if (dx * dx + dz * dz > SCAN_R * SCAN_R) continue;
                // Từ trên xuống, chỉ lấy khối cao nhất trong phạm vi có không khí ngay phía trên
                for (int dy = 3; dy >= -4; dy--) {
                    BlockPos p = base.add(dx, dy, dz);
                    BlockState s = world.getBlockState(p);
                    if (s.isAir()) continue;
                    if (!world.getBlockState(p.up()).isAir()) break;
                    double x = p.getX();
                    double y = p.getY();
                    double z = p.getZ();
                    if (s.getFluidState().getFluid() == Fluids.WATER) {
                        WATER.add(new double[] {x + 0.5, y + 0.88, z + 0.5});
                    } else if (s.getFluidState().isEmpty()) {
                        GROUND.add(new double[] {x, y + 1.01, z});
                    }
                    break;
                }
            }
        }
    }

    // ---------- Hạt môi trường ----------

    private static void drawAmbience(Matrix4f m, WorldBatch.V cam, MinecraftClient client, long now, float dt,
                                     double px, double py, double pz) {
        ElaraConfig.Layout l = ElaraConfig.layout(Feature.AMBIENCE);
        int style = clamp(l.get("style", 0), 0, AMBIENCE_STYLES - 1);
        int target = AMBIENCE_COUNT[clamp(l.get("density", 1), 0, 2)];
        if (style != partStyle) {
            PARTS.clear();
            partStyle = style;
        }
        while (PARTS.size() < target) PARTS.add(spawn(style, px, py, pz));
        while (PARTS.size() > target) PARTS.remove(PARTS.size() - 1);

        World world = client.world;
        int argb = ElaraConfig.color(Feature.AMBIENCE);
        double t = now / 1000.0;
        // Bụi sáng và bụi sao cộng sáng; cánh hoa và tuyết hòa trộn bình thường
        boolean additive = style <= 1;
        WorldBatch b = new WorldBatch(m, cam.x(), cam.y(), cam.z(), additive);
        for (Particle p : PARTS) {
            step(p, style, world, px, py, pz, dt, t);
            drawParticle(b, cam, p, style, argb, t);
        }
        b.flush();
    }

    private static Particle spawn(int style, double px, double py, double pz) {
        Particle p = new Particle();
        p.phase = RNG.nextDouble() * Math.PI * 2;
        respawn(p, style, px, py, pz);
        return p;
    }

    /** Đặt lại hạt ở một vị trí ngẫu nhiên quanh người chơi, rơi từ trên cao nếu là cánh hoa hoặc tuyết */
    private static void respawn(Particle p, int style, double px, double py, double pz) {
        p.x = px + (RNG.nextDouble() * 2 - 1) * RANGE;
        p.z = pz + (RNG.nextDouble() * 2 - 1) * RANGE;
        p.landed = false;
        p.landedAt = 0;
        p.spin = RNG.nextDouble() * Math.PI * 2;
        if (style == 2 || style == 3) {
            p.y = py + 4 + RNG.nextDouble() * 5;
            p.vy = style == 2 ? -0.9 - RNG.nextDouble() * 0.3 : -1.4 - RNG.nextDouble() * 0.4;
        } else {
            p.y = py - 1 + RNG.nextDouble() * 6;
            p.vy = (RNG.nextDouble() - 0.5) * 0.1;
        }
        p.vx = (RNG.nextDouble() - 0.5) * 0.25;
        p.vz = (RNG.nextDouble() - 0.5) * 0.25;
    }

    private static void step(Particle p, int style, World world, double px, double py, double pz, float dt, double t) {
        if (p.landed) {
            p.landedAt += dt;
            if (p.landedAt > LAND_S + FADE_S) respawn(p, style, px, py, pz);
            return;
        }
        switch (style) {
            case 2 -> {
                // Cánh hoa rơi xoay, trôi nhẹ theo gió giả. Chạm khối đặc thì đọng lại trên mặt đất.
                p.x += (Math.sin(t * 1.1 + p.phase) * 0.6 + 0.3) * dt;
                p.z += Math.cos(t * 0.9 + p.phase) * 0.4 * dt;
                p.y += p.vy * dt;
                p.spin += dt * (2.0 + p.phase * 0.5);
                BlockPos bp = BlockPos.ofFloored(p.x, p.y, p.z);
                BlockState s = world.getBlockState(bp);
                if (!s.isAir() && s.getFluidState().isEmpty()) {
                    p.landed = true;
                    p.y = bp.getY() + 1.02;
                    p.landedAt = 0;
                }
            }
            case 3 -> {
                // Bông tuyết rơi lắc nhẹ, chạm đất thì biến mất và xuất hiện lại từ trên cao
                p.x += Math.sin(t * 1.5 + p.phase) * 0.5 * dt;
                p.y += p.vy * dt;
                BlockPos bp = BlockPos.ofFloored(p.x, p.y, p.z);
                BlockState s = world.getBlockState(bp);
                if (!s.isAir() && s.getFluidState().isEmpty()) respawn(p, style, px, py, pz);
            }
            default -> {
                // Bụi sáng và bụi sao trôi chậm, lên xuống nhẹ
                p.x += p.vx * dt;
                p.z += p.vz * dt;
                p.y += (p.vy + Math.sin(t * 0.8 + p.phase) * 0.15) * dt;
            }
        }
        if (Math.abs(p.x - px) > RANGE || Math.abs(p.z - pz) > RANGE || p.y < py - 4 || p.y > py + 10) {
            respawn(p, style, px, py, pz);
        }
    }

    private static void drawParticle(WorldBatch b, WorldBatch.V cam, Particle p, int style, int argb, double t) {
        WorldBatch.V c = new WorldBatch.V(p.x, p.y, p.z);
        switch (style) {
            case 0 -> {
                float tw = (float) (0.6 + 0.4 * Math.sin(t * 2 + p.phase));
                quadBill(b, c, cam, 0.035, 0.035, WorldBatch.withAlpha(argb, 0.7f * tw));
            }
            case 1 -> {
                float tw = (float) (0.5 + 0.5 * Math.sin(t * 3 + p.phase));
                int col = 0xFF000000 | ColorMath.hsbToRgb((float) ((t * 0.15 + p.phase / 6.283) % 1.0), 0.45f, 1f);
                quadBill(b, c, cam, 0.03, 0.03, WorldBatch.withAlpha(col, 0.5f + 0.5f * tw));
            }
            case 2 -> {
                if (p.landed) {
                    float fade = (float) Math.max(0, 1 - Math.max(0, p.landedAt - LAND_S) / FADE_S);
                    flat(b, p.x, p.y, p.z, 0.09, 0.07, p.phase, WorldBatch.withAlpha(argb, 0.9f * fade));
                } else {
                    double hw = 0.09 * Math.abs(Math.cos(p.spin)) + 0.012;
                    quadBill(b, c, cam, hw, 0.07, WorldBatch.withAlpha(argb, 0.95f));
                }
            }
            default -> quadBill(b, c, cam, 0.035, 0.035, 0xE6FFFFFF);
        }
    }

    // ---------- Mist / Glass / Bokeh ----------

    private static void drawMist(Matrix4f m, WorldBatch.V cam, long now, double px, double py, double pz) {
        ElaraConfig.Layout l = ElaraConfig.layout(Feature.MIST_GLASS);
        int style = clamp(l.get("style", 0), 0, MIST_STYLES - 1);
        int count = MIST_COUNT[clamp(l.get("density", 1), 0, 2)];
        int argb = ElaraConfig.color(Feature.MIST_GLASS);
        double t = now / 1000.0;
        WorldBatch b = new WorldBatch(m, cam.x(), cam.y(), cam.z(), style != 0);
        for (int i = 0; i < count; i++) {
            // Góc vàng để các đốm không xếp thành hàng, xoay chậm theo thời gian
            double ang = i * 2.39996 + t * (0.05 + 0.01 * (i % 3));
            double r = 3 + ((i * 37) % 11) * 0.9;
            double x = px + Math.cos(ang) * r;
            double z = pz + Math.sin(ang) * r;
            switch (style) {
                case 0 -> {
                    double y = py + 0.25 + 0.15 * Math.sin(t * 0.5 + i);
                    flat(b, x, y, z, 2.5, 2.5, ang, WorldBatch.withAlpha(argb, 0.08f));
                }
                case 1 -> {
                    double y = py + 1.0 + (i % 5) * 0.35;
                    float pulse = (float) Math.pow(0.5 + 0.5 * Math.sin(t * 3 + i * 1.7), 4);
                    if (pulse > 0.05f) {
                        b.billboardBeam(new WorldBatch.V(x, y - 0.25, z), new WorldBatch.V(x, y + 0.25, z), cam,
                                0.035, WorldBatch.withAlpha(argb, pulse));
                    }
                }
                default -> {
                    double y = py + 1.2 + 0.8 * Math.sin(t * 0.4 + i);
                    double rad = 0.35 + (i % 4) * 0.22;
                    disc(b, new WorldBatch.V(x, y, z), cam, rad, WorldBatch.withAlpha(argb, 0.22f));
                }
            }
        }
        b.flush();
    }

    /** Đĩa tám cạnh nhìn về camera, màu đều */
    private static void disc(WorldBatch b, WorldBatch.V c, WorldBatch.V cam, double r, int argb) {
        WorldBatch.V[] bs = basis(c, cam);
        WorldBatch.V right = bs[0].mul(r);
        WorldBatch.V up = bs[1].mul(r);
        for (int k = 0; k < 8; k++) {
            double a0 = k * Math.PI / 4;
            double a1 = (k + 1) * Math.PI / 4;
            WorldBatch.V p0 = c.add(right.mul(Math.cos(a0))).add(up.mul(Math.sin(a0)));
            WorldBatch.V p1 = c.add(right.mul(Math.cos(a1))).add(up.mul(Math.sin(a1)));
            b.tri(c, p0, p1, argb);
        }
    }

    // ---------- Hình học dùng chung ----------

    /** Hình chữ nhật nhìn về camera, nửa cạnh hw và hh */
    private static void quadBill(WorldBatch b, WorldBatch.V c, WorldBatch.V cam, double hw, double hh, int argb) {
        WorldBatch.V[] bs = basis(c, cam);
        WorldBatch.V r = bs[0].mul(hw);
        WorldBatch.V u = bs[1].mul(hh);
        b.quad(c.sub(r).sub(u), c.add(r).sub(u), c.add(r).add(u), c.sub(r).add(u), argb);
    }

    /** Hình chữ nhật nằm ngang, xoay góc ang quanh trục đứng, nửa cạnh hr theo phải và hf theo trước */
    private static void flat(WorldBatch b, double cx, double cy, double cz, double hr, double hf, double ang, int argb) {
        WorldBatch.V c = new WorldBatch.V(cx, cy, cz);
        WorldBatch.V r = new WorldBatch.V(Math.cos(ang) * hr, 0, Math.sin(ang) * hr);
        WorldBatch.V f = new WorldBatch.V(-Math.sin(ang) * hf, 0, Math.cos(ang) * hf);
        b.quad(c.sub(r).sub(f), c.add(r).sub(f), c.add(r).add(f), c.sub(r).add(f), argb);
    }

    /** Trục phải và trục lên của mặt nhìn về camera, tại điểm c */
    private static WorldBatch.V[] basis(WorldBatch.V c, WorldBatch.V cam) {
        WorldBatch.V toCam = unit(cam.sub(c));
        WorldBatch.V right = unit(cross(UP, toCam));
        WorldBatch.V up = cross(toCam, right);
        return new WorldBatch.V[] {right, up};
    }

    private static int style(Feature f, int count) {
        return clamp(ElaraConfig.layout(f).get("style", 0), 0, count - 1);
    }

    private static Vec3d rgb(int argb) {
        return new Vec3d(((argb >> 16) & 0xFF) / 255.0, ((argb >> 8) & 0xFF) / 255.0, (argb & 0xFF) / 255.0);
    }

    private static int mixArgb(int a, int b, float k) {
        int aa = Math.round(((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * k);
        int rr = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int gg = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bb = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    private static WorldBatch.V cross(WorldBatch.V a, WorldBatch.V o) {
        return new WorldBatch.V(a.y() * o.z() - a.z() * o.y(),
                a.z() * o.x() - a.x() * o.z(),
                a.x() * o.y() - a.y() * o.x());
    }

    /** Vector đơn vị. Vector gần bằng 0 thì trả về (0, 0, 1) để không chia cho 0. */
    private static WorldBatch.V unit(WorldBatch.V v) {
        double len = Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
        if (len < 1e-9) return new WorldBatch.V(0, 0, 1);
        return v.mul(1 / len);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
