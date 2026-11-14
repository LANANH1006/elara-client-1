package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Điểm vào vẽ hiệu ứng trong thế giới: vòng nhảy và cánh ảo. */
public final class Effects {

    private Effects() {}

    public static void render(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        boolean rings = ElaraConfig.isOn(Feature.JUMP_RING);
        boolean wings = ElaraConfig.isOn(Feature.VIRTUAL_WINGS);
        if (!rings && !wings) return;

        Vec3d cam = ctx.camera().getPos();
        Matrix4f m = ctx.matrixStack().peek().getPositionMatrix();
        float a = PlayerTracker.alpha();

        RenderSystem.enableBlend();
        if (rings) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, false);
            JumpRings.draw(b, client, ElaraConfig.layout(Feature.JUMP_RING), ElaraConfig.color(Feature.JUMP_RING));
            b.flush();
        }
        if (wings) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            Wings.draw(b, client, ElaraConfig.layout(Feature.VIRTUAL_WINGS),
                    ElaraConfig.color(Feature.VIRTUAL_WINGS), a);
            b.flush();
        }
    }
}
