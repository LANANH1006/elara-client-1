package com.elara.client.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ẩn particle, entity và block entity không cần vẽ:
 *  - Quá xa so với khoảng cấu hình (entity và block entity: 8 đến 48 khối)
 *  - Nằm sau lưng camera (kiểm tra bằng hướng nhìn; khung nhìn vẫn do game xử lý)
 *  - Bị tường/khối đặc che khuất (kiểm tra bằng tia từ camera)
 *
 * Khi FPS thấp hơn 45, bỏ bớt khoảng 70% hạt khói, nước bắn và vụn block.
 * Người chơi khác luôn được vẽ, vì PvP cần thấy đối thủ.
 * Ngân sách tia giới hạn số lần raycast mỗi tick để không tự gây lag.
 */
public final class CullingManager {
    /** Khoảng cấu hình cho entity và block entity (khối) */
    public static final int RANGE_MIN = 8;
    public static final int RANGE_MAX = 48;
    /** Particle xa hơn khoảng này (8 đến 32 khối, chỉnh trong cài đặt Fix Lag hạt) bị bỏ */
    private static final double PARTICLE_RANGE_DEFAULT = 16.0;
    private static final double ALWAYS_VISIBLE = 6.0;
    private static final int RAY_BUDGET = 40;
    /** Dưới mức FPS này thì bớt hạt khói, nước bắn, vụn block */
    private static final int SHED_FPS = 45;
    /** Tỉ lệ phần trăm hạt bị bỏ khi FPS thấp */
    private static final int SHED_PERCENT = 70;

    private static int rays = RAY_BUDGET;

    private CullingManager() {}

    /** Gọi mỗi client tick để làm mới ngân sách tia */
    public static void tick() {
        rays = RAY_BUDGET;
    }

    /** true nếu particle cần vẽ */
    public static boolean keepParticle(double x, double y, double z) {
        if (!ElaraConfig.isOn(Feature.LAG_PARTICLES)) return true;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.gameRenderer == null) return true;

        Vec3d cam = c.gameRenderer.getCamera().getPos();
        double range = ElaraConfig.layout(Feature.LAG_PARTICLES).get("dist", (int) PARTICLE_RANGE_DEFAULT);
        double d2 = cam.squaredDistanceTo(x, y, z);
        if (d2 > range * range) return false;
        if (d2 < ALWAYS_VISIBLE * ALWAYS_VISIBLE) return true;
        return visible(c, cam, new Vec3d(x, y, z));
    }

    /** true nếu particle nên bị bỏ vì FPS thấp: khói, nước bắn, vụn block bị cắt khoảng 70% */
    public static boolean shedParticle(Particle particle) {
        if (!ElaraConfig.isOn(Feature.LAG_PARTICLES)) return false;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.getCurrentFps() >= SHED_FPS) return false;
        String name = particle.getClass().getSimpleName();
        boolean cheap = name.contains("Smoke") || name.contains("Splash")
                || name.contains("Dust") || name.contains("Digging") || name.contains("Debris");
        return cheap && ThreadLocalRandom.current().nextInt(100) < SHED_PERCENT;
    }

    /** true nếu entity cần vẽ */
    public static boolean keepEntity(Entity e) {
        if (!ElaraConfig.isOn(Feature.LAG_ENTITIES)) return true;
        if (e instanceof PlayerEntity) return true;

        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.gameRenderer == null) return true;

        Camera camera = c.gameRenderer.getCamera();
        Vec3d cam = camera.getPos();
        Vec3d mid = e.getPos().add(0, e.getHeight() / 2.0, 0);
        double range = entityRange();
        double d2 = cam.squaredDistanceTo(mid);
        if (d2 > range * range) return false;
        if (behind(camera, mid, Math.max(e.getWidth(), e.getHeight()) + 2.0)) return false;
        if (d2 < ALWAYS_VISIBLE * ALWAYS_VISIBLE) return true;
        return visible(c, cam, mid);
    }

    /** true nếu block entity (rương, biển, đầu...) cần vẽ: ẩn khi xa hơn khoảng cấu hình hoặc sau lưng camera */
    public static boolean keepBlockEntity(BlockPos pos) {
        if (!ElaraConfig.isOn(Feature.LAG_BLOCK_ENTITIES)) return true;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.gameRenderer == null) return true;

        Camera camera = c.gameRenderer.getCamera();
        double range = clampRange(ElaraConfig.layout(Feature.LAG_BLOCK_ENTITIES).get("dist", 24));
        Vec3d mid = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (camera.getPos().squaredDistanceTo(mid) > range * range) return false;
        return !behind(camera, mid, 1.5);
    }

    /** Khoảng vẽ entity đã cấu hình, luôn nằm trong 8 đến 48 khối */
    public static double entityRange() {
        return clampRange(ElaraConfig.layout(Feature.LAG_ENTITIES).get("dist", RANGE_MAX));
    }

    private static int clampRange(int v) {
        return Math.max(RANGE_MIN, Math.min(RANGE_MAX, v));
    }

    /** true nếu điểm nằm hẳn phía sau camera, lùi thêm một biên an toàn margin */
    private static boolean behind(Camera camera, Vec3d p, double margin) {
        Vec3d cam = camera.getPos();
        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double along = (p.x - cam.x) * fx + (p.y - cam.y) * fy + (p.z - cam.z) * fz;
        return along < -margin;
    }

    private static boolean visible(MinecraftClient c, Vec3d from, Vec3d to) {
        if (rays <= 0) return true;
        rays--;
        RaycastContext ctx = new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, c.player);
        BlockHitResult hit = c.world.raycast(ctx);
        return hit.getType() == HitResult.Type.MISS;
    }
}
