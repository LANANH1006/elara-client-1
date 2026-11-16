package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.ui.IosStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vẽ nút của các màn hình phụ thành nút kính bo tròn kiểu iOS.
 * Nút vanilla được vẽ trong PressableWidget, nên target ở đó (ButtonWidget kế thừa từ nó).
 *
 * Tắt hẳn trên sảnh chính (TitleScreen): nút của Minecraft và mod nền (Glaze) tự vẽ như bình thường.
 * require = 0: nếu chữ ký không khớp thì chỉ mất lớp đẹp, game vẫn chạy.
 */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$iosButton(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!AuthorGuard.isValid()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen instanceof TitleScreen) return;

        ClickableWidget self = (ClickableWidget) (Object) this;
        int x = self.getX();
        int y = self.getY();
        int w = self.getWidth();
        int h = self.getHeight();
        int radius = Math.min(10, h / 2);

        IosStyle.glassPanel(ctx, x, y, w, h, radius);
        if (self.isHovered()) {
            IosStyle.rounded(ctx, x, y, w, h, radius, IosStyle.TILE);
        }

        ctx.drawCenteredTextWithShadow(client.textRenderer, self.getMessage(),
                x + w / 2, y + (h - 8) / 2, IosStyle.TEXT);
        ci.cancel();
    }
}
