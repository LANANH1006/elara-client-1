package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Điểm vào vẽ hiệu ứng trong thế giới. Mỗi tính năng bật thì vẽ một lượt riêng. */
public final class Effects {

    private Effects() {}

    public static void render(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Vec3d cam = ctx.camera().getPos();
        Matrix4f m = ctx.matrixStack().peek().getPositionMatrix();
        float a = PlayerTracker.alpha();

        if (ElaraConfig.isOn(Feature.JUMP_RING)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, false);
            JumpRings.draw(b, client, ElaraConfig.layout(Feature.JUMP_RING), ElaraConfig.color(Feature.JUMP_RING));
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.FOOTSTEP_AURA)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            Footsteps.draw(b, client, ElaraConfig.layout(Feature.FOOTSTEP_AURA),
                    ElaraConfig.color(Feature.FOOTSTEP_AURA));
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.HEAD_ACCESSORY)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            Halo.draw(b, client, ElaraConfig.layout(Feature.HEAD_ACCESSORY),
                    ElaraConfig.color(Feature.HEAD_ACCESSORY), a);
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.SWORD_TRAIL)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            SwordTrail.draw(b, client, ElaraConfig.layout(Feature.SWORD_TRAIL),
                    ElaraConfig.color(Feature.SWORD_TRAIL));
            b.flush();
        }
        WorldBatch.V camV = new WorldBatch.V(cam.x, cam.y, cam.z);
        if (ElaraConfig.isOn(Feature.PVP_REACH)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, false);
            Reach.draw(b, client, ElaraConfig.color(Feature.PVP_REACH), a);
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.RAIN_3D)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, false);
            Rain.draw(b, camV, ElaraConfig.layout(Feature.RAIN_3D), ElaraConfig.color(Feature.RAIN_3D));
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.HIT_EFFECT)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            HitFx.draw(b, camV, ElaraConfig.layout(Feature.HIT_EFFECT), ElaraConfig.color(Feature.HIT_EFFECT));
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.VIRTUAL_WINGS)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            Wings.draw(b, client, ElaraConfig.layout(Feature.VIRTUAL_WINGS),
                    ElaraConfig.color(Feature.VIRTUAL_WINGS), a, camV);
            b.flush();
        }
    }
}
