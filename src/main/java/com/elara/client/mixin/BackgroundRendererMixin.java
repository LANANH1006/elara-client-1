package com.elara.client.mixin;

import com.elara.client.core.Gate;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
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

    @Inject(method = "applyFog", at = @At("HEAD"), cancellable = true, require = 0)
    private static void elara$noFog(CallbackInfoReturnable<Fog> cir) {
        if (Gate.loading()) return;
        if (!ElaraConfig.isOn(Feature.NO_FOG)) return;
        // Chỉ tắt sương khi đang trong thế giới
        if (MinecraftClient.getInstance().world == null) return;
        cir.setReturnValue(Fog.DUMMY);
    }
}
