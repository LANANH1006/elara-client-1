package com.elara.client.hud;

import com.elara.client.core.ElaraConfig;
import com.elara.client.ui.IosStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Dynamic Island ở đầu màn hình. Phase 6 sẽ thay dòng chữ bằng thông tin bài hát. */
public final class DynamicIslandHud {
    private static final int MIN_WIDTH = 110;
    private static final int BG = 0xE6000000;

    private DynamicIslandHud() {}

    /** Vị trí và kích thước của đảo, tính theo pixel trên màn hình */
    public record Box(int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public static String line(MinecraftClient client) {
        return "Elara · " + client.getCurrentFps() + " FPS";
    }

    public static Box box(MinecraftClient client) {
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        int h = ElaraConfig.islandHeight();
        int w = Math.max(MIN_WIDTH, client.textRenderer.getWidth(line(client)) + 24);
        int x = ElaraConfig.islandX() < 0
                ? (sw - w) / 2
                : clamp(ElaraConfig.islandX(), 0, sw - w);
        int y = clamp(ElaraConfig.islandOffsetY(), 0, Math.max(0, sh - h));
        return new Box(x, y, w, h);
    }

    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;
        draw(ctx, client, box(client));
    }

    public static void draw(DrawContext ctx, MinecraftClient client, Box b) {
        IosStyle.rounded(ctx, b.x(), b.y(), b.w(), b.h(), b.h() / 2, BG);
        ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal(line(client)),
                b.x() + b.w() / 2, b.y() + (b.h() - 9) / 2 + 1, IosStyle.TEXT);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
