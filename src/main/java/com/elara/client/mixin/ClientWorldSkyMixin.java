package com.elara.client.mixin;

import com.elara.client.fx.WorldFx;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Đổi màu nền trời cho Bầu trời sống động và Chroma World. Màu sương theo chân trời lấy từ đây nên cũng đổi theo.
 * Khi hai tính năng đều tắt thì mixin không làm gì. require = 0: nếu không khớp thì trời vẫn như gốc.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldSkyMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void elara$skyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (!WorldFx.skyActive()) return;
        Vec3d base = cir.getReturnValue();
        if (base == null) return;
        cir.setReturnValue(WorldFx.skyColor(base));
    }
}
