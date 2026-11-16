package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.ElaraAssets;
import com.elara.client.core.Diag;
import com.elara.client.core.ElaraConfig;
import com.elara.client.ui.SceneScreen;
import com.elara.client.ui.TitleScene;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nền ảnh tĩnh title_bg cho các màn hình phụ (tùy chọn, đa người chơi, ...).
 *
 * Không đụng tới sảnh chính (TitleScreen), túi đồ, và màn hình tải (tải map, kết nối, tiến trình).
 * Những màn hình đó để Minecraft và mod nền của người dùng (ví dụ Glaze) tự vẽ.
 */
@Mixin(value = Screen.class, priority = Integer.MAX_VALUE)
public abstract class ScreenMixin {

    /** Đã ghi nhật ký lần đầu khi sảnh chính được vẽ, để không in lặp lại mỗi khung hình */
    private static boolean titleLogged;

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$iosBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!AuthorGuard.isValid()) return;
        if (!titleLogged && ((Object) this) instanceof TitleScreen) {
            titleLogged = true;
            Diag.log("sảnh chính: mod không vẽ nền, để Minecraft và mod nền vẽ");
        }
        if (!elara$drawsOwnBackground()) return;
        elara$drawBackdrop(ctx);
        ci.cancel();
    }

    @Inject(method = "renderPanoramaBackground(Lnet/minecraft/client/gui/DrawContext;F)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$iosPanorama(DrawContext ctx, float delta, CallbackInfo ci) {
        if (!AuthorGuard.isValid()) return;
        if (!elara$drawsOwnBackground()) return;
        elara$drawBackdrop(ctx);
        ci.cancel();
    }

    /** Chỉ các màn hình phụ mới nhận nền Elara. Sảnh chính, túi đồ và màn hình tải giữ nguyên của game. */
    private boolean elara$drawsOwnBackground() {
        Object self = this;
        return !(self instanceof TitleScreen)
                && !(self instanceof HandledScreen<?>)
                && !(self instanceof DownloadingTerrainScreen)
                && !(self instanceof ProgressScreen)
                && !(self instanceof MessageScreen);
    }

    /** Vẽ nền Elara: cảnh 3D chỉ ở màn chọn cảnh, còn lại là ảnh tĩnh kèm lớp tối */
    private void elara$drawBackdrop(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        boolean scene = ElaraConfig.titleMode() == 1 && ((Object) this) instanceof SceneScreen;
        if (scene && TitleScene.draw(ctx, w, h)) return;

        if (ElaraAssets.ensureTitleBg()) {
            ctx.drawTexture(RenderLayer::getGuiTextured, ElaraAssets.TITLE_BG, 0, 0, 0f, 0f, w, h, w, h);
        }
        ctx.fill(0, 0, w, h, 0x88000000);
    }
}
