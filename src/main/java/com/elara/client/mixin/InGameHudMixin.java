package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ẩn tâm ngắm gốc của game khi tâm tùy chỉnh của Elara đang bật */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$hideVanillaCrosshair(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
        if (!AuthorGuard.isValid()) return;
        if (ElaraConfig.isOn(Feature.PIXEL_CROSSHAIR)) ci.cancel();
    }
}
