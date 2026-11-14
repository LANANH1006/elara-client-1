package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.ui.LoadingBackground;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Màn hình khởi động của Minecraft (logo Mojang, thanh tải). Không chặn render của vanilla,
 * vì vanilla dùng render đó để biết khi nào tải xong. Thay vào đó vẽ nền Elara đè lên sau khi
 * vanilla vẽ xong, nên logo và nền gốc (có thể bị resource pack đổi) không hiện ra.
 */
@Mixin(value = SplashOverlay.class, priority = Integer.MAX_VALUE)
public abstract class SplashOverlayMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("RETURN"), require = 0)
    private void elara$iosLoading(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!AuthorGuard.isValid()) return;
        LoadingBackground.draw(ctx);
    }
}
