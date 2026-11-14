package com.elara.client.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Ẩn particle và entity không cần vẽ:
 *  - Quá xa so với tầm nhìn cần thiết
 *  - Bị tường/khối đặc che khuất (kiểm tra bằng tia từ camera)
 *
 * Người chơi khác luôn được vẽ, vì PvP cần thấy đối thủ.
 * Ngân sách tia giới hạn số lần raycast mỗi tick để không tự gây lag.
 */
public final class CullingManager {
    /** Particle xa hơn 16 khối bị bỏ khi bật Fix Lag hạt */
    private static final double PARTICLE_RANGE = 16.0;
    private static final double ENTITY_RANGE = 96.0;
    private static final double ALWAYS_VISIBLE = 6.0;
    private static final int RAY_BUDGET = 40;

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
        double d2 = cam.squaredDistanceTo(x, y, z);
        if (d2 > PARTICLE_RANGE * PARTICLE_RANGE) return false;
        if (d2 < ALWAYS_VISIBLE * ALWAYS_VISIBLE) return true;
        return visible(c, cam, new Vec3d(x, y, z));
    }

    /** true nếu entity cần vẽ */
    public static boolean keepEntity(Entity e) {
        if (!ElaraConfig.isOn(Feature.LAG_ENTITIES)) return true;
        if (e instanceof PlayerEntity) return true;

        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.gameRenderer == null) return true;

        Vec3d cam = c.gameRenderer.getCamera().getPos();
        Vec3d mid = e.getPos().add(0, e.getHeight() / 2.0, 0);
        double d2 = cam.squaredDistanceTo(mid);
        if (d2 > ENTITY_RANGE * ENTITY_RANGE) return false;
        if (d2 < ALWAYS_VISIBLE * ALWAYS_VISIBLE) return true;
        return visible(c, cam, mid);
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
