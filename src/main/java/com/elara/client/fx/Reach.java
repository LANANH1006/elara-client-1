package com.elara.client.fx;

import com.elara.client.ui.IosStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

/**
 * Vòng tầm đánh 3 block. Nếu đang nhắm vào thực thể thì vòng nằm quanh thực thể đó,
 * ngược lại nằm quanh nhân vật. Vòng chuyển màu xanh khi mục tiêu đã nằm trong tầm đánh.
 */
public final class Reach {
    public static final double RANGE = 3.0;

    private Reach() {}

    public static void draw(WorldBatch b, MinecraftClient client, int argb, float a) {
        ClientPlayerEntity me = client.player;
        if (me == null) return;

        Entity target = client.targetedEntity;
        WorldBatch.V center;
        int col = argb;
        if (target != null) {
            center = new WorldBatch.V(target.getX(), target.getY() + 0.03, target.getZ());
            if (me.distanceTo(target) <= RANGE) {
                col = IosStyle.GREEN;
            }
        } else {
            PlayerTracker.Track t = PlayerTracker.tracks().get(me.getUuid());
            double px = t == null ? me.getX() : PlayerTracker.lerp(t.px, t.x, a);
            double pz = t == null ? me.getZ() : PlayerTracker.lerp(t.pz, t.z, a);
            double py = t == null ? me.getY() : PlayerTracker.lerp(t.py, t.y, a);
            center = new WorldBatch.V(px, py + 0.03, pz);
        }
        b.ring(center, RANGE, 0.06, 64, 0, col);
    }
}
