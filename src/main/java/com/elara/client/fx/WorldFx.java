package com.elara.client.fx;

import com.elara.client.core.Guard;

import com.elara.client.core.ColorMath;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Thế giới sống động, chỉ dùng WorldBatch (tam giác và đoạn màu, không shader, không framebuffer).
 *
 * Quy tắc để máy điện thoại mát:
 *  - Mọi việc đọc block (tìm mặt nước, mặt đất, mái che, điểm chạm của cánh hoa) chỉ chạy trong tick.
 *    Mỗi tick chỉ kiểm tra tối đa PROBES_PER_TICK tọa độ ngẫu nhiên, ghi vào ring buffer cố định.
 *  - Hàm vẽ chỉ đọc trạng thái đã tính sẵn, không quét block và không đổi mảng.
 *  - Tính năng tắt thì hàm vẽ thoát ngay dòng đầu.
 *  - Mọi lỗi trong tick hoặc vẽ được bắt, tắt riêng phần đó và báo một lần.
 */
public final class WorldFx {
    public static final int SKY_STYLES = 9;
    public static final int AMBIENCE_STYLES = 4;
    public static final int MIST_STYLES = 3;
    /** Số gợn tối đa, mảng cấp phát một lần */
    public static final int RIPPLE_MAX = 60;
    /** Mặc định cho điện thoại */
    public static final int RIPPLE_DEFAULT = 30;

    /** Số hạt tối đa, cũng là kích thước mảng hạt */
    private static final int PART_MAX = 50;
    /** Ngân sách quét: số tọa độ kiểm tra ngẫu nhiên mỗi tick */
    private static final int PROBES_PER_TICK = 16;
    private static final int SAMPLE_CAP = 64;
    private static final int SCAN_R = 12;
    private static final int WET_QUADS = 24;
    private static final double RANGE = 9.0;
    private static final long RIPPLE_LIFE_MS = 900L;
    /** Một tick của game dài 50 ms */
    private static final double TICK_S = 0.05;
    private static final double LAND_S = 3.0;
    private static final double FADE_S = 1.0;
    /** Khoảng cách vẽ bầu trời */
    private static final double SKY_R = 160.0;

    /** Màu nền trời theo kiểu (chỉ lấy RGB) */
    private static final int[] SKY_COLORS = {
            0xFF0B1030, 0xFF2A0E45, 0xFFE08A52, 0xFF7DBEFF, 0xFF06152A,
            0xFF050818, 0xFF3A0A0A, 0xFF1B2D44, 0xFFF3B8D0
    };
    private static final int[] AMBIENCE_COUNT = {20, 35, PART_MAX};
    private static final int[] MIST_COUNT = {8, 16, 26};

    private static final WorldBatch.V UP = new WorldBatch.V(0, 1, 0);
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

    // Hạt môi trường: mảng nguyên thủy, cấp phát một lần, không tạo object mỗi khung hình
    private static final double[] PX = new double[PART_MAX];
    private static final double[] PY = new double[PART_MAX];
    private static final double[] PZ = new double[PART_MAX];
    /** Vị trí ở tick trước, để nội suy mượt khi vẽ */
    private static final double[] OX = new double[PART_MAX];
    private static final double[] OY = new double[PART_MAX];
    private static final double[] OZ = new double[PART_MAX];
    private static final double[] PVX = new double[PART_MAX];
    private static final double[] PVY = new double[PART_MAX];
    private static final double[] PVZ = new double[PART_MAX];
    private static final double[] PSPIN = new double[PART_MAX];
    private static final double[] PPHASE = new double[PART_MAX];
    private static final double[] PLAND_AT = new double[PART_MAX];
    private static final boolean[] PLANDED = new boolean[PART_MAX];
    private static int partCount;
    private static int partStyle = -1;

    // Mẫu mặt nước và mặt đất, ghi vòng quanh (ring buffer), đọc khi vẽ
    private static final double[] WX = new double[SAMPLE_CAP];
    private static final double[] WY = new double[SAMPLE_CAP];
    private static final double[] WZ = new double[SAMPLE_CAP];
    private static final double[] GX = new double[SAMPLE_CAP];
    private static final double[] GY = new double[SAMPLE_CAP];
    private static final double[] GZ = new double[SAMPLE_CAP];
    private static int waterCount;
    private static int waterHead;
    private static int groundCount;
    private static int groundHead;

    // Gợn sóng: mỗi ô có tọa độ và mốc thời gian, mốc 0 nghĩa là ô trống
    private static final double[] RX = new double[RIPPLE_MAX];
    private static final double[] RY = new double[RIPPLE_MAX];
    private static final double[] RZ = new double[RIPPLE_MAX];
    private static final long[] RT = new long[RIPPLE_MAX];
    private static int rippleHead;
    private static float rippleCredit;
    private static boolean raining;

    /** Con trỏ tái sử dụng cho mọi lần đọc block, không tạo BlockPos mới */
    private static final BlockPos.Mutable CURSOR = new BlockPos.Mutable();
    private static final Random RNG = new Random();
    private static boolean brokenTick;
    private static boolean brokenDraw;

    private WorldFx() {}

    /** Có lớp thế giới nào đang vẽ không. Chroma World không tính ở đây vì nó đổi màu trời, không vẽ. */
    public static boolean active() {
        return ElaraConfig.isOn(Feature.WORLD_SKY) || ElaraConfig.isOn(Feature.RAIN_RIPPLE)
                || ElaraConfig.isOn(Feature.AMBIENCE) || ElaraConfig.isOn(Feature.MIST_GLASS);
    }

    /** Có đang đổi màu trời không: Bầu trời sống động hoặc Chroma World */
    public static boolean skyActive() {
        return ElaraConfig.isOn(Feature.WORLD_SKY) || ElaraConfig.isOn(Feature.CHROMA_WORLD)
                || ElaraConfig.isOn(Feature.LITE_SHADER);
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
        if (ElaraConfig.isOn(Feature.LITE_SHADER)) {
            // Elara Lite Shader: trời trong hơn và ấm hơn một chút, chỉ đổi màu nền trời, không cần shader
            out = new Vec3d(Math.min(1.0, out.x * 0.97), Math.min(1.0, out.y * 1.03), Math.min(1.0, out.z * 1.10));
        }
        return out;
    }

    // ---------- Tick: mọi việc đọc block nằm ở đây ----------

    /** Gọi mỗi tick (20 lần mỗi giây) từ ElaraClient. Không gọi trong hàm vẽ. */
    public static void tick(MinecraftClient client) {
        // Tắt hết tính năng thế giới thì không tính gì cả
        if (brokenTick || !(active() || skyActive())) return;
        try {
            tickInner(client);
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            brokenTick = true;
            partCount = 0;
            rippleCredit = 0f;
            raining = false;
            System.err.println("[Elara] Tắt tính toán Thế giới do lỗi: " + t);
        }
    }

    private static void tickInner(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            partCount = 0;
            raining = false;
            rippleCredit = 0f;
            return;
        }
        if (ElaraConfig.isOn(Feature.RAIN_RIPPLE)) {
            tickRain(client, client.world);
        } else {
            raining = false;
            rippleCredit = 0f;
        }
        if (ElaraConfig.isOn(Feature.AMBIENCE)) {
            tickParticles(client, client.world);
        } else {
            partCount = 0;
            partStyle = -1;
        }
    }

    private static void tickRain(MinecraftClient client, World world) {
        ElaraConfig.Layout l = ElaraConfig.layout(Feature.RAIN_RIPPLE);
        raining = world.isRaining() || l.get("force", 0) == 1;
        if (!raining) {
            rippleCredit = 0f;
            return;
        }
        probe(world, client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ());
        if (waterCount == 0) return;

        int cap = clamp(l.get("count", RIPPLE_DEFAULT), 5, RIPPLE_MAX);
        // Tốc độ sinh gợn sao cho khoảng cap gợn sống đủ 0.9 giây
        rippleCredit += (float) (cap * TICK_S / (RIPPLE_LIFE_MS / 1000.0));
        long now = System.currentTimeMillis();
        while (rippleCredit >= 1f) {
            rippleCredit -= 1f;
            int s = RNG.nextInt(waterCount);
            int slot = rippleHead % cap;
            rippleHead = (rippleHead + 1) & 0x3FFFFFFF;
            RX[slot] = WX[s];
            RY[slot] = WY[s];
            RZ[slot] = WZ[s];
            RT[slot] = now;
        }
    }

    /**
     * Ngân sách quét: kiểm tra PROBES_PER_TICK tọa độ ngẫu nhiên quanh người chơi, dùng chung một BlockPos.
     * Ghi mặt nước và mặt đất có không khí phía trên và thấy được bầu trời. Dưới mái che thì bỏ qua.
     */
    private static void probe(World world, int bx, int by, int bz) {
        for (int i = 0; i < PROBES_PER_TICK; i++) {
            int x = bx + RNG.nextInt(2 * SCAN_R + 1) - SCAN_R;
            int z = bz + RNG.nextInt(2 * SCAN_R + 1) - SCAN_R;
            int y = by + RNG.nextInt(8) - 4;
            int dx = x - bx;
            int dz = z - bz;
            if (dx * dx + dz * dz > SCAN_R * SCAN_R) continue;
            CURSOR.set(x, y, z);
            BlockState s = world.getBlockState(CURSOR);
            if (s.isAir()) continue;
            CURSOR.set(x, y + 1, z);
            if (!world.getBlockState(CURSOR).isAir()) continue;
            if (!world.isSkyVisible(CURSOR)) continue;
            if (s.getFluidState().getFluid() == Fluids.WATER) {
                addWater(x + 0.5, y + 0.88, z + 0.5);
            } else if (s.getFluidState().isEmpty()) {
                addGround(x, y + 1.01, z);
            }
        }
    }

    private static void addWater(double x, double y, double z) {
        WX[waterHead] = x;
        WY[waterHead] = y;
        WZ[waterHead] = z;
        waterHead = (waterHead + 1) % SAMPLE_CAP;
        if (waterCount < SAMPLE_CAP) waterCount++;
    }

    private static void addGround(double x, double y, double z) {
        GX[groundHead] = x;
        GY[groundHead] = y;
        GZ[groundHead] = z;
        groundHead = (groundHead + 1) % SAMPLE_CAP;
        if (groundCount < SAMPLE_CAP) groundCount++;
    }

    private static void tickParticles(MinecraftClient client, World world) {
        ElaraConfig.Layout l = ElaraConfig.layout(Feature.AMBIENCE);
        int style = clamp(l.get("style", 0), 0, AMBIENCE_STYLES - 1);
        int target = AMBIENCE_COUNT[clamp(l.get("density", 1), 0, 2)];
        if (style != partStyle) {
            partStyle = style;
            partCount = 0;
        }
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
        if (partCount > target) partCount = target;
        while (partCount < target) {
            PPHASE[partCount] = RNG.nextDouble() * Math.PI * 2;
            respawn(partCount, style, px, py, pz);
            partCount++;
        }
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < partCount; i++) {
            OX[i] = PX[i];
            OY[i] = PY[i];
            OZ[i] = PZ[i];
            step(i, style, world, px, py, pz, t);
        }
    }

    /** Đặt lại hạt ở một vị trí ngẫu nhiên quanh người chơi, rơi từ trên cao nếu là cánh hoa hoặc tuyết */
    private static void respawn(int i, int style, double px, double py, double pz) {
        PX[i] = px + (RNG.nextDouble() * 2 - 1) * RANGE;
        PZ[i] = pz + (RNG.nextDouble() * 2 - 1) * RANGE;
        PLANDED[i] = false;
        PLAND_AT[i] = 0;
        PSPIN[i] = RNG.nextDouble() * Math.PI * 2;
        if (style == 2 || style == 3) {
            PY[i] = py + 4 + RNG.nextDouble() * 5;
            PVY[i] = style == 2 ? -0.9 - RNG.nextDouble() * 0.3 : -1.4 - RNG.nextDouble() * 0.4;
        } else {
            PY[i] = py - 1 + RNG.nextDouble() * 6;
            PVY[i] = (RNG.nextDouble() - 0.5) * 0.1;
        }
        PVX[i] = (RNG.nextDouble() - 0.5) * 0.25;
        PVZ[i] = (RNG.nextDouble() - 0.5) * 0.25;
        OX[i] = PX[i];
        OY[i] = PY[i];
        OZ[i] = PZ[i];
    }

    private static void step(int i, int style, World world, double px, double py, double pz, double t) {
        if (PLANDED[i]) {
            PLAND_AT[i] += TICK_S;
            if (PLAND_AT[i] > LAND_S + FADE_S) respawn(i, style, px, py, pz);
            return;
        }
        switch (style) {
            case 2 -> {
                // Cánh hoa rơi xoay, trôi nhẹ theo gió giả. Chạm khối đặc có trời phía trên thì đọng lại.
                PX[i] += (Math.sin(t * 1.1 + PPHASE[i]) * 0.6 + 0.3) * TICK_S;
                PZ[i] += Math.cos(t * 0.9 + PPHASE[i]) * 0.4 * TICK_S;
                PY[i] += PVY[i] * TICK_S;
                PSPIN[i] += TICK_S * (2.0 + PPHASE[i] * 0.5);
                if (solidAt(world, PX[i], PY[i], PZ[i])) {
                    int bx = (int) Math.floor(PX[i]);
                    int by = (int) Math.floor(PY[i]);
                    int bz = (int) Math.floor(PZ[i]);
                    CURSOR.set(bx, by + 1, bz);
                    if (world.isSkyVisible(CURSOR)) {
                        PLANDED[i] = true;
                        PY[i] = by + 1.02;
                        PLAND_AT[i] = 0;
                    } else {
                        respawn(i, style, px, py, pz);
                    }
                }
            }
            case 3 -> {
                // Bông tuyết rơi lắc nhẹ, chạm khối đặc thì xuất hiện lại từ trên cao
                PX[i] += Math.sin(t * 1.5 + PPHASE[i]) * 0.5 * TICK_S;
                PY[i] += PVY[i] * TICK_S;
                if (solidAt(world, PX[i], PY[i], PZ[i])) respawn(i, style, px, py, pz);
            }
            default -> {
                // Bụi sáng và bụi sao trôi chậm, lên xuống nhẹ
                PX[i] += PVX[i] * TICK_S;
                PZ[i] += PVZ[i] * TICK_S;
                PY[i] += (PVY[i] + Math.sin(t * 0.8 + PPHASE[i]) * 0.15) * TICK_S;
            }
        }
        if (Math.abs(PX[i] - px) > RANGE || Math.abs(PZ[i] - pz) > RANGE || PY[i] < py - 4 || PY[i] > py + 10) {
            respawn(i, style, px, py, pz);
        }
    }

    /** Khối đặc (không phải không khí, không phải chất lỏng) tại điểm này. Chỉ gọi trong tick. */
    private static boolean solidAt(World world, double x, double y, double z) {
        CURSOR.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        BlockState s = world.getBlockState(CURSOR);
        return !s.isAir() && s.getFluidState().isEmpty();
    }

    // ---------- Vẽ: chỉ đọc trạng thái đã tính sẵn ----------

    /** Vẽ mọi lớp thế giới đang bật. Gọi trong AFTER_TRANSLUCENT, sau khi địa hình đã vẽ. */
    public static void draw(Matrix4f m, WorldBatch.V cam, MinecraftClient client) {
        if (brokenDraw || client.world == null || client.player == null || !active()) return;
        try {
            drawInner(m, cam, client);
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            brokenDraw = true;
            System.err.println("[Elara] Tắt Thế giới sống động do lỗi: " + t);
        }
    }

    private static void drawInner(Matrix4f m, WorldBatch.V cam, MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (ElaraConfig.isOn(Feature.WORLD_SKY)) drawSky(m, cam, now);
        if (ElaraConfig.isOn(Feature.RAIN_RIPPLE) && raining) drawRipples(m, cam, now);
        if (ElaraConfig.isOn(Feature.AMBIENCE) && partCount > 0) drawAmbience(m, cam, now);
        if (ElaraConfig.isOn(Feature.MIST_GLASS)) {
            drawMist(m, cam, now, client.player.getX(), client.player.getY(), client.player.getZ());
        }
    }

    // ---------- Bầu trời ----------

    private static void drawSky(Matrix4f m, WorldBatch.V cam, long now) {
        int style = style(Feature.WORLD_SKY, SKY_STYLES);
        double t = now / 1000.0;
        // Một batch cho cả bầu trời: WorldBatch dùng chung Tessellator, hai batch cùng lúc sẽ xung đột.
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

    // ---------- Gợn sóng mưa và mặt đất ướt ----------

    /** Vẽ gợn từ mảng RT/RX đã tính ở tick. Mặt đất ướt chỉ vẽ khi bật trong cài đặt. */
    private static void drawRipples(Matrix4f m, WorldBatch.V cam, long now) {
        ElaraConfig.Layout l = ElaraConfig.layout(Feature.RAIN_RIPPLE);
        int cap = clamp(l.get("count", RIPPLE_DEFAULT), 5, RIPPLE_MAX);
        int argb = ElaraConfig.color(Feature.RAIN_RIPPLE);
        WorldBatch ring = new WorldBatch(m, cam.x(), cam.y(), cam.z(), false);
        for (int i = 0; i < cap; i++) {
            if (RT[i] == 0L) continue;
            long age = now - RT[i];
            if (age < 0 || age > RIPPLE_LIFE_MS) continue;
            double a = age / (double) RIPPLE_LIFE_MS;
            double rad = 0.08 + 0.6 * a;
            double thick = 0.03 * (1 - a) + 0.012;
            ring.ring(new WorldBatch.V(RX[i], RY[i], RZ[i]), rad, thick, 10, 0,
                    WorldBatch.withAlpha(argb, (float) (0.85 * (1 - a))));
        }
        ring.flush();

        if (l.get("wet", 0) == 1 && groundCount > 0) drawSheen(m, cam, now);
    }

    /** Vệt sáng bầu trời lướt nhẹ trên đất ướt, lấy mẫu có chu kỳ từ mảng đã tính */
    private static void drawSheen(Matrix4f m, WorldBatch.V cam, long now) {
        WorldBatch sheen = new WorldBatch(m, cam.x(), cam.y(), cam.z(), true);
        double t = now / 1000.0;
        int n = Math.min(WET_QUADS, groundCount);
        for (int i = 0; i < n; i++) {
            int idx = (int) ((now / 250L + i * 7L) % groundCount);
            double pulse = 0.5 + 0.5 * Math.sin(t * 3 + GX[idx] * 1.3 + GZ[idx] * 0.7);
            int c = WorldBatch.withAlpha(0xFFDDEEFF, (float) (0.04 + 0.06 * pulse));
            flat(sheen, GX[idx] + 0.5, GY[idx], GZ[idx] + 0.5, 0.5, 0.5, 0, c);
        }
        sheen.flush();
    }

    // ---------- Hạt môi trường ----------

    private static void drawAmbience(Matrix4f m, WorldBatch.V cam, long now) {
        ElaraConfig.Layout l = ElaraConfig.layout(Feature.AMBIENCE);
        int style = clamp(l.get("style", 0), 0, AMBIENCE_STYLES - 1);
        int argb = ElaraConfig.color(Feature.AMBIENCE);
        double t = now / 1000.0;
        float k = PlayerTracker.alpha();
        // Bụi sáng và bụi sao cộng sáng; cánh hoa và tuyết hòa trộn bình thường
        boolean additive = style <= 1;
        WorldBatch b = new WorldBatch(m, cam.x(), cam.y(), cam.z(), additive);
        for (int i = 0; i < partCount; i++) {
            double x = OX[i] + (PX[i] - OX[i]) * k;
            double y = OY[i] + (PY[i] - OY[i]) * k;
            double z = OZ[i] + (PZ[i] - OZ[i]) * k;
            drawParticle(b, cam, i, x, y, z, style, argb, t);
        }
        b.flush();
    }

    private static void drawParticle(WorldBatch b, WorldBatch.V cam, int i, double x, double y, double z,
                                     int style, int argb, double t) {
        WorldBatch.V c = new WorldBatch.V(x, y, z);
        switch (style) {
            case 0 -> {
                float tw = (float) (0.6 + 0.4 * Math.sin(t * 2 + PPHASE[i]));
                quadBill(b, c, cam, 0.035, 0.035, WorldBatch.withAlpha(argb, 0.7f * tw));
            }
            case 1 -> {
                float tw = (float) (0.5 + 0.5 * Math.sin(t * 3 + PPHASE[i]));
                int col = 0xFF000000 | ColorMath.hsbToRgb((float) ((t * 0.15 + PPHASE[i] / 6.283) % 1.0), 0.45f, 1f);
                quadBill(b, c, cam, 0.03, 0.03, WorldBatch.withAlpha(col, 0.5f + 0.5f * tw));
            }
            case 2 -> {
                if (PLANDED[i]) {
                    float fade = (float) Math.max(0, 1 - Math.max(0, PLAND_AT[i] - LAND_S) / FADE_S);
                    flat(b, x, y, z, 0.09, 0.07, PPHASE[i], WorldBatch.withAlpha(argb, 0.9f * fade));
                } else {
                    double hw = 0.09 * Math.abs(Math.cos(PSPIN[i])) + 0.012;
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
