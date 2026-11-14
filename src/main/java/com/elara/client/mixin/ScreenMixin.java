package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.ElaraAssets;
import com.elara.client.core.ElaraConfig;
import com.elara.client.ui.SceneScreen;
import com.elara.client.ui.TitleScene;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thay nền của mọi màn hình (menu, chat, options, multiplayer...) bằng title_bg.png,
 * ảnh này được nạp từ trong jar nên resource pack không đè được.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$iosBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!AuthorGuard.isValid()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        // Màn hình chính và bảng chọn Scene có thể dùng cảnh 3D thay cho ảnh tĩnh
        boolean scene = ElaraConfig.titleMode() == 1
                && (((Object) this) instanceof TitleScreen || ((Object) this) instanceof SceneScreen);
        if (scene && TitleScene.draw(ctx, w, h)) {
            ci.cancel();
            return;
        }

        ElaraAssets.ensureLoaded();
        ctx.drawTexture(RenderLayer::getGuiTextured, ElaraAssets.TITLE_BG, 0, 0, 0f, 0f, w, h, w, h);
        ctx.fill(0, 0, w, h, 0x88000000);
        ci.cancel();
    }
}
