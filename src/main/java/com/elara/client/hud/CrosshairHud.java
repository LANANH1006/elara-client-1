package com.elara.client.hud;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.ui.IosStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Tâm ngắm tùy chỉnh: dấu cộng, chấm tròn, chữ X, vòng tròn.
 * Tâm gốc của game được ẩn bởi InGameHudMixin.
 */
public final class CrosshairHud {
    private static final double TAU = Math.PI * 2;

    private CrosshairHud() {}

    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null || client.options.hudHidden || client.player == null) return;
        if (!ElaraConfig.isOn(Feature.PIXEL_CROSSHAIR)) return;

        ElaraConfig.Layout l = ElaraConfig.layout(Feature.PIXEL_CROSSHAIR);
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        int cx = sw * l.xPct / 100;
        int cy = sh * l.yPct / 100;
        float s = l.scalePct / 100f;
        int len = Math.max(1, Math.round(l.get("length", 6) * s));
        int thick = Math.max(1, Math.round(l.get("thickness", 2) * s));
        int gap = Math.round(l.get("gap", 3) * s);
        int color = ElaraConfig.color(Feature.PIXEL_CROSSHAIR);

        switch (l.get("shape", 0)) {
            case 1 -> dot(ctx, cx, cy, thick, color);
            case 2 -> cross(ctx, cx, cy, gap, len, thick, color);
            case 3 -> ring(ctx, cx, cy, gap + len, thick, color);
            default -> plus(ctx, cx, cy, gap, len, thick, color);
        }
    }

    private static void plus(DrawContext ctx, int cx, int cy, int gap, int len, int t, int c) {
        int h = t / 2;
        ctx.fill(cx - gap - len, cy - h, cx - gap, cy - h + t, c);
        ctx.fill(cx + gap, cy - h, cx + gap + len, cy - h + t, c);
        ctx.fill(cx - h, cy - gap - len, cx - h + t, cy - gap, c);
        ctx.fill(cx - h, cy + gap, cx - h + t, cy + gap + len, c);
    }

    private static void dot(DrawContext ctx, int cx, int cy, int t, int c) {
        IosStyle.rounded(ctx, cx - t, cy - t, t * 2, t * 2, t, c);
    }

    private static void cross(DrawContext ctx, int cx, int cy, int gap, int len, int t, int c) {
        int h = t / 2;
        for (int i = 0; i < len; i++) {
            int d = gap + i;
            square(ctx, cx - d - h, cy - d - h, t, c);
            square(ctx, cx + d - h, cy - d - h, t, c);
            square(ctx, cx - d - h, cy + d - h, t, c);
            square(ctx, cx + d - h, cy + d - h, t, c);
        }
    }

    private static void ring(DrawContext ctx, int cx, int cy, int r, int t, int c) {
        int n = Math.max(24, (int) Math.round(TAU * r * 1.5));
        for (int k = 0; k < n; k++) {
            double a = TAU * k / n;
            int x = (int) Math.round(cx + r * Math.cos(a));
            int y = (int) Math.round(cy + r * Math.sin(a));
            square(ctx, x - t / 2, y - t / 2, t, c);
        }
    }

    private static void square(DrawContext ctx, int x, int y, int t, int c) {
        ctx.fill(x, y, x + t, y + t, c);
    }
}
