package com.elara.client.mixin;

import com.elara.client.fx.LowFire;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lửa cháy góc nhìn thứ nhất: khi bật Lửa hạ thấp thì đẩy ma trận xuống theo độ hạ và đặt màu lửa
 * trước khi game vẽ, rồi khôi phục sau khi vẽ xong. Lửa vẫn là lửa gốc của game, chỉ đổi vị trí và màu.
 * require = 0: nếu chữ ký phương thức không khớp thì mixin bị bỏ qua và lửa gốc vẫn hiện như cũ.
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {

    @Inject(
            method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"),
            require = 0
    )
    private static void elara$lowFireHead(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (!LowFire.enabled()) return;
        matrices.push();
        matrices.translate(0.0f, -LowFire.offset(), 0.0f);
        int c = LowFire.tint();
        RenderSystem.setShaderColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1f);
    }

    @Inject(
            method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("RETURN"),
            require = 0
    )
    private static void elara$lowFireReturn(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (!LowFire.enabled()) return;
        matrices.pop();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
