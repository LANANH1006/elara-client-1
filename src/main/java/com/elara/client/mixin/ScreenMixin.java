package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thay nền của mọi màn hình bằng nền kiểu iOS.
 * Màn hình chính dùng title_bg.png, các màn hình khác dùng lớp tối bán trong suốt.
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

        if ((Object) this instanceof TitleScreen) {
            Identifier bg = Identifier.of("elaraclient", "textures/gui/title_bg.png");
            ctx.drawTexture(RenderLayer::getGuiTextured, bg, 0, 0, 0f, 0f, w, h, w, h);
            ctx.fill(0, 0, w, h, 0x55000000);
        } else {
            ctx.fill(0, 0, w, h, 0xB8000000);
        }
        ci.cancel();
    }
}
