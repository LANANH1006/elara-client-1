package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.ui.LoadingBackground;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nền kiểu dirt/options của vanilla. Trên bản 1.21.4 phương thức này không tồn tại, nên mixin
 * không khớp và bỏ qua (require = 0). Nền màn hình tải được xử lý ở ScreenMixin.
 * Priority lớn để chạy trước các mixin khác; mod khác vẫn có thể hủy hoặc ghi đè.
 */
@Mixin(value = Screen.class, priority = Integer.MAX_VALUE)
public abstract class ScreenBackgroundMixin {

    @Inject(method = "renderBackgroundTexture(Lnet/minecraft/client/gui/DrawContext;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$iosBackgroundTexture(DrawContext ctx, CallbackInfo ci) {
        if (!AuthorGuard.isValid()) return;
        LoadingBackground.drawBackdrop(ctx);
        ci.cancel();
    }
}
