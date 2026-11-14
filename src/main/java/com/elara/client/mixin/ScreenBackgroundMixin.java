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
 * Nền kiểu dirt/options của vanilla, dùng ở màn hình tải thế giới, kết nối server và
 * tải địa hình (ProgressScreen, LevelLoadingScreen, ConnectScreen, DownloadingTerrainScreen
 * đều kế thừa từ Screen). Thay bằng nền Elara để resource pack không đè được.
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
