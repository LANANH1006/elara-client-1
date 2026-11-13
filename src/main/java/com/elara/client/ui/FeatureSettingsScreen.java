package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Bảng cài đặt riêng của một tính năng: vị trí, tỉ lệ, màu. Mở bằng chuột phải ở menu Elara. */
public class FeatureSettingsScreen extends Screen {
    private static final int[] COLORS = {
            0xFFFFFFFF, 0xFF30D158, 0xFF0A84FF, 0xFFFF9F0A, 0xFFFF453A, 0xFFBF5AF2
    };
    private static final int PAD = 12;
    private static final int ROW_H = 26;
    private static final int TITLE_H = 30;
    private static final int SWATCH = 18;
    private static final int SWATCH_START = 44;

    private final Screen parent;
    private final Feature feature;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int dragging = -1;

    public FeatureSettingsScreen(Screen parent, Feature feature) {
        super(Text.literal(feature.name()));
        this.parent = parent;
        this.feature = feature;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 300);
        ph = TITLE_H + ROW_H * 4 + 16;
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    private ElaraConfig.Layout layout() {
        return ElaraConfig.layout(feature);
    }

    private int rowY(int row) { return py + TITLE_H + row * ROW_H; }
    private int trackX() { return px + PAD; }
    private int trackW() { return pw - PAD * 2; }

    private int swatchX(int i) {
        int span = pw - PAD * 2 - SWATCH_START - SWATCH;
        return px + PAD + SWATCH_START + i * span / (COLORS.length - 1);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        IosStyle.glassPanel(ctx, px, py, pw, ph, 14);
        ElaraConfig.Layout l = layout();
        ctx.drawText(textRenderer, Text.literal(feature.label()), px + PAD, py + 10, IosStyle.TEXT, false);

        drawSlider(ctx, 0, Lang.get("set.x") + ": " + l.xPct + "%", fraction(l.xPct, 0, 100));
        drawSlider(ctx, 1, Lang.get("set.y") + ": " + l.yPct + "%", fraction(l.yPct, 0, 100));
        drawSlider(ctx, 2, Lang.get("set.scale") + ": " + l.scalePct + "%", fraction(l.scalePct, 50, 200));

        int ry = rowY(3);
        int cy = ry + 4;
        ctx.drawText(textRenderer, Text.literal(Lang.get("set.color")), px + PAD, ry + 9, IosStyle.TEXT, false);
        for (int i = 0; i < COLORS.length; i++) {
            int sx = swatchX(i);
            if (COLORS[i] == l.color) {
                IosStyle.rounded(ctx, sx - 2, cy - 2, SWATCH + 4, SWATCH + 4, (SWATCH + 4) / 2, IosStyle.TEXT);
            }
            IosStyle.rounded(ctx, sx, cy, SWATCH, SWATCH, SWATCH / 2, COLORS[i]);
        }
    }

    private void drawSlider(DrawContext ctx, int row, String label, float frac) {
        int ry = rowY(row);
        ctx.drawText(textRenderer, Text.literal(label), px + PAD, ry + 1, IosStyle.TEXT, false);
        int ty = ry + 14;
        IosStyle.rounded(ctx, trackX(), ty, trackW(), 6, 3, IosStyle.GRAY);
        IosStyle.rounded(ctx, trackX(), ty, Math.max(6, Math.round(trackW() * frac)), 6, 3, IosStyle.ACCENT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        for (int i = 0; i < 3; i++) {
            if (inside(mx, my, trackX() - 4, rowY(i) + 10, trackW() + 8, 14)) {
                dragging = i;
                applySlider(i, mx);
                return true;
            }
        }

        int cy = rowY(3) + 4;
        for (int i = 0; i < COLORS.length; i++) {
            if (inside(mx, my, swatchX(i) - 2, cy - 2, SWATCH + 4, SWATCH + 4)) {
                layout().color = COLORS[i];
                ElaraConfig.save();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging >= 0) {
            applySlider(dragging, mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging >= 0) {
            dragging = -1;
            ElaraConfig.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void applySlider(int i, double mx) {
        float frac = (float) Math.max(0, Math.min(1, (mx - trackX()) / (double) trackW()));
        ElaraConfig.Layout l = layout();
        if (i == 0) {
            l.xPct = Math.round(frac * 100);
        } else if (i == 1) {
            l.yPct = Math.round(frac * 100);
        } else {
            l.scalePct = 50 + Math.round(frac * 150);
        }
    }

    private static float fraction(int v, int min, int max) {
        return Math.max(0f, Math.min(1f, (v - min) / (float) (max - min)));
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
