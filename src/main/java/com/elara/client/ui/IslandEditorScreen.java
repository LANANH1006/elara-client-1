package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.hud.DynamicIslandHud;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Kéo thả Dynamic Island tới vị trí mong muốn. ESC để lưu và quay lại menu. */
public class IslandEditorScreen extends Screen {
    private static final int BTN_W = 120;
    private static final int BTN_H = 18;

    private final Screen parent;
    private boolean dragging = false;
    private int grabX;
    private int grabY;

    public IslandEditorScreen(Screen parent) {
        super(Text.literal("Elara · Dynamic Island"));
        this.parent = parent;
    }

    private int btnX() { return (width - BTN_W) / 2; }
    private int btnY() { return height / 2 + 10; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);

        MinecraftClient client = MinecraftClient.getInstance();
        DynamicIslandHud.draw(ctx, client, DynamicIslandHud.box(client));

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("ui.editor_hint")),
                width / 2, height / 2 - 14, IosStyle.TEXT);
        IosStyle.rounded(ctx, btnX(), btnY(), BTN_W, BTN_H, 9, IosStyle.GLASS);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("ui.reset")),
                width / 2, btnY() + 5, IosStyle.TEXT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        if (mx >= btnX() && mx < btnX() + BTN_W && my >= btnY() && my < btnY() + BTN_H) {
            ElaraConfig.setIslandX(-1);
            ElaraConfig.setIslandOffsetY(6);
            ElaraConfig.save();
            return true;
        }

        DynamicIslandHud.Box b = DynamicIslandHud.box(MinecraftClient.getInstance());
        if (b.contains(mx, my)) {
            dragging = true;
            grabX = (int) mx - b.x();
            grabY = (int) my - b.y();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            ElaraConfig.setIslandX((int) mx - grabX);
            ElaraConfig.setIslandOffsetY((int) my - grabY);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            ElaraConfig.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void close() {
        ElaraConfig.save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
