package com.elara.client.fx;

import com.elara.client.core.Guard;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Bóng mềm dưới chân người chơi và thực thể gần: vài đĩa tròn mờ xếp chồng nhau, nhìn như bóng tán xạ.
 * Độ cao so với mặt đất được tính trong tick (tối đa 6 khối dò xuống, dùng chung một BlockPos).
 * Càng nhảy cao bóng càng mờ, đến khoảng 2.5 khối thì biến mất.
 * Mặc định tắt. Bật trong tab Thế giới.
 */
public final class SoftShadow {
    private static final int MAX = 16;
    private static final double PLAYER_R = 24.0;
    private static final double MOB_R = 16.0;
    /** Độ cao tối đa bóng còn thấy được */
    private static final double FADE_HEIGHT = 2.5;
    private static final int SEGS = 12;
    private static final int LAYERS = 3;

    private static final double[] SX = new double[MAX];
    private static final double[] SY = new double[MAX];
    private static final double[] SZ = new double[MAX];
    private static final double[] SR = new double[MAX];
    private static final double[] SA = new double[MAX];
    private static int count;
    private static boolean broken;
    private static final BlockPos.Mutable CURSOR = new BlockPos.Mutable();

    private SoftShadow() {}

    /** Gọi mỗi tick. Tính vị trí và độ cao của các bóng. Không vẽ gì ở đây. */
    public static void tick(MinecraftClient client) {
        if (broken || !enabled()) return;
        try {
            tickInner(client);
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            broken = true;
            count = 0;
            System.err.println("[Elara] Tắt Bóng mềm do lỗi: " + t);
        }
    }

    /** Bóng mềm bật khi tính năng Bóng mềm hoặc Elara Lite Shader được bật */
    public static boolean enabled() {
        return ElaraConfig.isOn(Feature.SOFT_SHADOW) || ElaraConfig.isOn(Feature.LITE_SHADER);
    }

    private static void tickInner(MinecraftClient client) {
        count = 0;
        if (!enabled()) return;
        if (client.world == null || client.player == null) return;
        World world = client.world;
        PlayerEntity me = client.player;

        double r2 = PLAYER_R * PLAYER_R;
        for (PlayerEntity p : world.getPlayers()) {
            if (count >= MAX) break;
            if (!p.isAlive() || me.squaredDistanceTo(p) > r2) continue;
            add(world, p.getX(), p.getY(), p.getZ(), 0.42);
        }
        Box box = me.getBoundingBox().expand(MOB_R);
        List<LivingEntity> mobs = world.getEntitiesByClass(LivingEntity.class, box,
                e -> !(e instanceof PlayerEntity) && e.isAlive());
        for (LivingEntity e : mobs) {
            if (count >= MAX) break;
            add(world, e.getX(), e.getY(), e.getZ(), Math.max(0.25, Math.min(0.7, e.getWidth() * 0.6)));
        }
    }

    /** Dò xuống từ chân để tìm mặt đất. Không tìm thấy thì không vẽ bóng cho thực thể này. */
    private static void add(World world, double x, double y, double z, double r) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int start = (int) Math.floor(y - 0.001);
        for (int d = 0; d <= 6; d++) {
            int by = start - d;
            CURSOR.set(bx, by, bz);
            BlockState s = world.getBlockState(CURSOR);
            if (s.isAir() || !s.getFluidState().isEmpty()) continue;
            double ground = by + 1.0;
            double air = y - ground;
            double a = Math.max(0.0, Math.min(1.0, 1.0 - air / FADE_HEIGHT));
            if (a > 0.0) {
                SX[count] = x;
                SY[count] = ground;
                SZ[count] = z;
                SR[count] = r;
                SA[count] = a;
                count++;
            }
            return;
        }
    }

    /** Vẽ các đĩa bóng. Chỉ đọc mảng đã tính ở tick. */
    public static void draw(Matrix4f m, WorldBatch.V cam) {
        if (broken || count == 0 || !enabled()) return;
        WorldBatch b = new WorldBatch(m, cam.x(), cam.y(), cam.z(), false);
        double strength = ElaraConfig.layout(Feature.SOFT_SHADOW).get("strength", 45) / 100.0;
        for (int i = 0; i < count; i++) {
            double a = SA[i] * strength;
            if (a <= 0.01) continue;
            for (int k = 0; k < LAYERS; k++) {
                double rr = SR[i] * (1.0 - 0.28 * k);
                disc(b, SX[i], SY[i] + 0.02, SZ[i], rr, WorldBatch.withAlpha(0xFF000000, (float) (a * 0.22)));
            }
        }
        b.flush();
    }

    /** Đĩa nằm ngang tâm (cx, cy, cz), bán kính r, màu đều */
    private static void disc(WorldBatch b, double cx, double cy, double cz, double r, int argb) {
        WorldBatch.V c = new WorldBatch.V(cx, cy, cz);
        for (int k = 0; k < SEGS; k++) {
            double a0 = 2 * Math.PI * k / SEGS;
            double a1 = 2 * Math.PI * (k + 1) / SEGS;
            WorldBatch.V p0 = new WorldBatch.V(cx + r * Math.cos(a0), cy, cz + r * Math.sin(a0));
            WorldBatch.V p1 = new WorldBatch.V(cx + r * Math.cos(a1), cy, cz + r * Math.sin(a1));
            b.tri(c, p0, p1, argb);
        }
    }
}
