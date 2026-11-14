package com.elara.client.ui;

import com.elara.client.core.ColorMath;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Bảng chọn màu: ô bão hòa/độ sáng, thanh cầu vồng, thanh trong suốt và nút Chroma. */
public class ColorPickerScreen extends Screen {
    private static final int PAD = 12;
    private static final int SB_W = 160;
    private static final int SB_H = 100;
    private static final int CELL = 4;
    private static final int SEG = 3;
    private static final int BAR_H = 10;

    private static final int DRAG_NONE = 0;
    private static final int DRAG_SB = 1;
    private static final int DRAG_HUE = 2;
    private static final int DRAG_ALPHA = 3;

    private final Screen parent;
    private final Feature feature;
    private int px;
    private int py;
    private int pw;
    private final int ph = 210;
    private int drag = DRAG_NONE;

    public ColorPickerScreen(Screen parent, Feature feature) {
        super(Text.literal("Color"));
        this.parent = parent;
        this.feature = feature;
        ElaraConfig.Layout l = ElaraConfig.layout(feature);
        if (l.get("hue", -1) < 0) {
            float[] hsb = ColorMath.rgbToHsb(l.color & 0xFFFFFF);
            l.put("hue", Math.round(hsb[0] * 359f));
            l.put("sat", Math.round(hsb[1] * 100f));
            l.put("bri", Math.round(hsb[2] * 100f));
            l.put("alpha", Math.round(((l.color >>> 24) & 0xFF) * 100f / 255f));
        }
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 290);
        px = (width - pw) / 2;
        py = Math.max(8, (height - ph) / 2);
    }

    private int sbX() { return px + PAD; }
    private int sbY() { return py + 34; }
    private int barX() { return px + PAD; }
    private int barW() { return pw - PAD * 2; }
    private int hueY() { return py + 146; }
    private int alphaY() { return py + 164; }
    private int chromaY() { return py + 186; }
    private int toggleX() { return px + pw - PAD - 30; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        IosStyle.glassPanel(ctx, px, py, pw, ph, 14);
        ElaraConfig.Layout l = ElaraConfig.layout(feature);
        int hue = l.get("hue", 0);
        int sat = l.get("sat", 100);
        int bri = l.get("bri", 100);
        int alpha = l.get("alpha", 100);
        ctx.drawText(textRenderer, Text.literal(Lang.get("picker.title")), px + PAD, py + 10, IosStyle.TEXT, false);

        // Ô bão hòa (ngang) và độ sáng (dọc)
        int sx = sbX();
        int sy = sbY();
        for (int x = 0; x < SB_W; x += CELL) {
            for (int y = 0; y < SB_H; y += CELL) {
                float s = (x + CELL / 2f) / SB_W;
                float v = 1f - (y + CELL / 2f) / SB_H;
                int rgb = ColorMath.hsbToRgb(hue / 360f, s, v);
                ctx.fill(sx + x, sy + y, sx + Math.min(SB_W, x + CELL), sy + Math.min(SB_H, y + CELL),
                        0xFF000000 | rgb);
            }
        }
        int markX = sx + Math.round(SB_W * sat / 100f);
        int markY = sy + Math.round(SB_H * (1f - bri / 100f));
        ctx.drawBorder(markX - 3, markY - 3, 7, 7, 0xFFFFFFFF);

        // Ô xem trước màu đang dùng (kể cả khi Chroma đang chạy)
        int prevX = sx + SB_W + PAD;
        IosStyle.rounded(ctx, prevX, sy, px + pw - PAD - prevX, 40, 8, ElaraConfig.color(feature));

        // Thanh cầu vồng
        int hx = barX();
        int hy = hueY();
        for (int x = 0; x < barW(); x += SEG) {
            int seg = Math.min(SEG, barW() - x);
            float h = x / (float) barW();
            ctx.fill(hx + x, hy, hx + x + seg, hy + BAR_H, 0xFF000000 | ColorMath.hsbToRgb(h, 1f, 1f));
        }
        ctx.drawBorder(hx + Math.round(barW() * hue / 360f) - 2, hy - 2, 5, BAR_H + 4, 0xFFFFFFFF);

        // Thanh độ trong suốt: từ trong suốt đến màu đã chọn
        int ay = alphaY();
        int base = ColorMath.hsbToRgb(hue / 360f, sat / 100f, bri / 100f);
        for (int x = 0; x < barW(); x += SEG) {
            int seg = Math.min(SEG, barW() - x);
            float t = x / (float) barW();
            ctx.fill(hx + x, ay, hx + x + seg, ay + BAR_H, IosStyle.withAlpha(base, t));
        }
        ctx.drawBorder(hx + Math.round(barW() * alpha / 100f) - 2, ay - 2, 5, BAR_H + 4, 0xFFFFFFFF);

        // Nút Chroma
        int cy = chromaY();
        ctx.drawText(textRenderer, Text.literal(Lang.get("picker.chroma")), px + PAD, cy + 5, IosStyle.TEXT, false);
        IosStyle.toggleSwitch(ctx, toggleX(), cy + 2, l.get("chroma", 0) == 1, "chroma:" + feature.name());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (inside(mx, my, sbX(), sbY(), SB_W, SB_H)) {
            drag = DRAG_SB;
            updateSb(mx, my);
            return true;
        }
        if (inside(mx, my, barX(), hueY() - 4, barW(), BAR_H + 8)) {
            drag = DRAG_HUE;
            updateHue(mx);
            return true;
        }
        if (inside(mx, my, barX(), alphaY() - 4, barW(), BAR_H + 8)) {
            drag = DRAG_ALPHA;
            updateAlpha(mx);
            return true;
        }
        if (inside(mx, my, px + PAD, chromaY() - 2, pw - PAD * 2, 22)) {
            ElaraConfig.Layout l = ElaraConfig.layout(feature);
            l.put("chroma", l.get("chroma", 0) == 1 ? 0 : 1);
            ElaraConfig.save();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag == DRAG_SB) {
            updateSb(mx, my);
            return true;
        }
        if (drag == DRAG_HUE) {
            updateHue(mx);
            return true;
        }
        if (drag == DRAG_ALPHA) {
            updateAlpha(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag != DRAG_NONE) {
            drag = DRAG_NONE;
            ElaraConfig.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void updateSb(double mx, double my) {
        ElaraConfig.Layout l = ElaraConfig.layout(feature);
        int sat = Math.round(clamp01((mx - sbX()) / SB_W) * 100f);
        int bri = Math.round((1f - clamp01((my - sbY()) / SB_H)) * 100f);
        apply(l, l.get("hue", 0), sat, bri, l.get("alpha", 100));
    }

    private void updateHue(double mx) {
        ElaraConfig.Layout l = ElaraConfig.layout(feature);
        int hue = Math.round(clamp01((mx - barX()) / barW()) * 359f);
        apply(l, hue, l.get("sat", 100), l.get("bri", 100), l.get("alpha", 100));
    }

    private void updateAlpha(double mx) {
        ElaraConfig.Layout l = ElaraConfig.layout(feature);
        int alpha = Math.round(clamp01((mx - barX()) / barW()) * 100f);
        apply(l, l.get("hue", 0), l.get("sat", 100), l.get("bri", 100), alpha);
    }

    /** Lưu HSB và alpha, rồi tính lại màu ARGB cho tính năng */
    private static void apply(ElaraConfig.Layout l, int hue, int sat, int bri, int alpha) {
        l.put("hue", hue);
        l.put("sat", sat);
        l.put("bri", bri);
        l.put("alpha", alpha);
        int rgb = ColorMath.hsbToRgb(hue / 360f, sat / 100f, bri / 100f);
        l.color = (Math.round(alpha * 255f / 100f) << 24) | rgb;
    }

    private static float clamp01(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void close() {
        ElaraConfig.save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
