package com.elara.client.ui;

import com.elara.client.core.ElaraAssets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;

/** Nền và logo dùng cho các màn hình tải: lấy ảnh từ jar nên resource pack không đè được. */
public final class LoadingBackground {

    private LoadingBackground() {}

    /** Chỉ nền: ảnh title_bg kèm lớp tối */
    public static void drawBackdrop(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();
        ElaraAssets.ensureLoaded();
        ctx.drawTexture(RenderLayer::getGuiTextured, ElaraAssets.TITLE_BG, 0, 0, 0f, 0f, w, h, w, h);
        ctx.fill(0, 0, w, h, 0x88000000);
    }

    /** Nền kèm logo và tên mod, dùng cho màn hình khởi động */
    public static void draw(DrawContext ctx) {
        drawBackdrop(ctx);
        MinecraftClient client = MinecraftClient.getInstance();
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();
        AppleLogo.draw(ctx, w / 2, h / 2 - 12, 64, IosStyle.TEXT);
        ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Elara Client"),
                w / 2, h / 2 + 34, IosStyle.TEXT_DIM);
    }
}
