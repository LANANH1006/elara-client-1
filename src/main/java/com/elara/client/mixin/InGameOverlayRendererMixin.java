package com.elara.client.mixin;

import com.elara.client.core.Fault;
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
 * Cờ firePushed đảm bảo push và pop luôn cân bằng, kể cả khi lỗi xảy ra giữa chừng.
 * require = 0: nếu chữ ký phương thức không khớp thì mixin bị bỏ qua và lửa gốc vẫn hiện như cũ.
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {

    private static final String FAULT_KEY = "mixin.lowfire";

    /** HEAD đã push và RETURN chưa pop. Chỉ dùng trên luồng render */
    private static boolean firePushed;

    @Inject(
            method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"),
            require = 0
    )
    private static void elara$lowFireHead(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        firePushed = false;
        if (Fault.off(FAULT_KEY)) return;
        if (!LowFire.enabled()) return;
        matrices.push();
        firePushed = true;
        try {
            matrices.translate(0.0f, -LowFire.offset(), 0.0f);
            int c = LowFire.tint();
            RenderSystem.setShaderColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1f);
        } catch (Throwable t) {
            Fault.fail(FAULT_KEY, t);
        }
    }

    @Inject(
            method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("RETURN"),
            require = 0
    )
    private static void elara$lowFireReturn(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (!firePushed) return;
        firePushed = false;
        matrices.pop();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
