package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Kéo thả các khung HUD (phím bấm, thông số) tới chỗ mong muốn.
 * Vị trí lưu theo phần trăm màn hình. Kích thước khung là ước lượng, không phải kích thước đúng.
 */
public class HudEditorScreen extends Screen {
    private static final Feature[] ITEMS = {Feature.KEYSTROKES, Feature.SYSINFO};

    private final Screen parent;
    private Feature dragging = null;
    private double grabX;
    private double grabY;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("HUD"));
        this.parent = parent;
        IosStyle.beginOpen();
    }

    private static int boxW(Feature f) {
        return f == Feature.KEYSTROKES ? 60 : 120;
    }

    private static int boxH(Feature f) {
        return f == Feature.KEYSTROKES ? 92 : 60;
    }

    /** Hình chữ nhật của một HUD trên màn hình: x, y, w, h */
    private int[] rect(Feature f) {
        ElaraConfig.Layout l = ElaraConfig.layout(f);
        float s = l.scalePct / 100f;
        int x = Math.round(width * l.xPct / 100f);
        int y = Math.round(height * l.yPct / 100f);
        return new int[] {x, y, Math.round(boxW(f) * s), Math.round(boxH(f) * s)};
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("ui.hud_editor_hint")),
                width / 2, 8, IosStyle.TEXT);
        for (Feature f : ITEMS) {
            int[] r = rect(f);
            IosStyle.glass(ctx, r[0], r[1], r[2], r[3], 8);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(f.label()),
                    r[0] + r[2] / 2, r[1] + r[3] / 2 - 4, IosStyle.TEXT);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        for (Feature f : ITEMS) {
            int[] r = rect(f);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                dragging = f;
                grabX = mx - r[0];
                grabY = my - r[1];
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(mx, my, button, dx, dy);
        ElaraConfig.Layout l = ElaraConfig.layout(dragging);
        double x = mx - grabX;
        double y = my - grabY;
        l.xPct = clampPct(Math.round(x * 100.0 / Math.max(1, width)));
        l.yPct = clampPct(Math.round(y * 100.0 / Math.max(1, height)));
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            ElaraConfig.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private static int clampPct(long v) {
        return (int) Math.max(0, Math.min(95, v));
    }

    @Override
    public void close() {
        ElaraConfig.save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
