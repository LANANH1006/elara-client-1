package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.ElaraAssets;
import com.elara.client.core.ElaraConfig;
import com.elara.client.ui.LoadingBackground;
import com.elara.client.ui.SceneScreen;
import com.elara.client.ui.TitleScene;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thay nền của mọi màn hình (menu, chat, options, multiplayer...) bằng title_bg.png hoặc cảnh 3D.
 * Ảnh được nạp từ trong jar nên resource pack không đè được.
 *
 * Priority lớn để mixin này chạy trước các mixin khác đang cùng chạm vào renderBackground
 * (ví dụ addon kiểu Glaze). Priority chỉ quyết định thứ tự, mod khác vẫn có thể hủy lệnh này.
 */
@Mixin(value = Screen.class, priority = Integer.MAX_VALUE)
public abstract class ScreenMixin {

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$iosBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!AuthorGuard.isValid()) return;
        elara$drawBackdrop(ctx);
        ci.cancel();
    }

    /** Panorama là nền xoay của menu chính. Chặn luôn để không mod nào vẽ đè lên nền Elara. */
    @Inject(method = "renderPanoramaBackground(Lnet/minecraft/client/gui/DrawContext;F)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$iosPanorama(DrawContext ctx, float delta, CallbackInfo ci) {
        if (!AuthorGuard.isValid()) return;
        elara$drawBackdrop(ctx);
        ci.cancel();
    }

    /** Vẽ nền Elara cho màn hình hiện tại: cảnh 3D nếu đang bật ở màn hình chính, ngược lại ảnh tĩnh */
    private void elara$drawBackdrop(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        // Màn hình tải địa hình, tải thế giới, kết nối: một lần vẽ gradient, nhẹ nhất lúc đang tải map
        Object self = this;
        if (self instanceof DownloadingTerrainScreen || self instanceof LevelLoadingScreen
                || self instanceof ProgressScreen || self instanceof MessageScreen) {
            LoadingBackground.drawGradient(ctx, w, h);
            return;
        }

        boolean scene = ElaraConfig.titleMode() == 1
                && (((Object) this) instanceof TitleScreen || ((Object) this) instanceof SceneScreen);
        if (scene && TitleScene.draw(ctx, w, h)) return;

        ElaraAssets.ensureLoaded();
        ctx.drawTexture(RenderLayer::getGuiTextured, ElaraAssets.TITLE_BG, 0, 0, 0f, 0f, w, h, w, h);
        ctx.fill(0, 0, w, h, 0x88000000);
    }
}
