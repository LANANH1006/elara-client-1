package com.elara.client.mixin;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.core.Gate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Fog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tắt sương mù: khi bật, hàm tính sương trả về Fog.DUMMY (không có sương) */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {

    private static final String FAULT_KEY = "mixin.fog";

    @Inject(method = "applyFog", at = @At("HEAD"), cancellable = true, require = 0)
    private static void elara$noFog(CallbackInfoReturnable<Fog> cir) {
        if (Fault.off(FAULT_KEY)) return;
        try {
            if (Gate.loading()) return;
            if (!ElaraConfig.isOn(Feature.NO_FOG)) return;
            // Chỉ tắt sương khi đang trong thế giới
            if (MinecraftClient.getInstance().world == null) return;
            cir.setReturnValue(Fog.DUMMY);
        } catch (Throwable t) {
            Fault.fail(FAULT_KEY, t);
        }
    }
}
