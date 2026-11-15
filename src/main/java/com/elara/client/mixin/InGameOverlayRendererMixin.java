package com.elara.client.mixin;

import com.elara.client.fx.LowFire;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lửa cháy góc nhìn thứ nhất: khi bật Lửa hạ thấp tùy chỉnh thì hủy lớp lửa gốc và để LowFire vẽ lại.
 * require = 0: nếu chữ ký phương thức không khớp thì mixin bị bỏ qua và lửa gốc vẫn hiện.
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {

    @Inject(
            method = "renderFireOverlay(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void elara$lowFire(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        if (LowFire.enabled()) {
            LowFire.markHook();
            ci.cancel();
        }
    }
}
