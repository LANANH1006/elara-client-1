package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.hud.HudBounds;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Kéo thả các khung HUD tới chỗ mong muốn. Kích thước khung là kích thước thật của lần vẽ gần nhất
 * (xem HudBounds). Vị trí lưu theo pixel GUI, giữ khung nằm gọn trong màn hình.
 */
public class HudEditorScreen extends Screen {
    private static final Feature[] ITEMS = {Feature.KEYSTROKES, Feature.SYSINFO, Feature.TARGET_HUD};

    private final Screen parent;
    private Feature dragging = null;
    private double grabX;
    private double grabY;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("HUD"));
        this.parent = parent;
        IosStyle.beginOpen();
    }

    /** Vị trí và kích thước của khung HUD trên màn hình, theo pixel GUI: x, y, rộng, cao */
    private int[] rect(Feature f) {
        ElaraConfig.Layout l = ElaraConfig.layout(f);
        float s = l.scalePct / 100f;
        int x = l.xPx >= 0 ? l.xPx : Math.round(width * l.xPct / 100f);
        int y = l.yPx >= 0 ? l.yPx : Math.round(height * l.yPct / 100f);
        return new int[] {x, y, Math.round(HudBounds.width(f) * s), Math.round(HudBounds.height(f) * s)};
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
        int[] size = rect(dragging);
        int nx = clamp((int) Math.round(mx - grabX), 0, Math.max(0, width - size[2]));
        int ny = clamp((int) Math.round(my - grabY), 0, Math.max(0, height - size[3]));
        ElaraConfig.Layout l = ElaraConfig.layout(dragging);
        l.xPx = nx;
        l.yPx = ny;
        l.xPct = Math.round(nx * 100f / Math.max(1, width));
        l.yPct = Math.round(ny * 100f / Math.max(1, height));
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

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void close() {
        ElaraConfig.save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
