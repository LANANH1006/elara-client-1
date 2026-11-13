package com.elara.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Màn hình khởi động kiểu iPhone: nền đen, táo cắn dở hiện dần rồi mờ đi */
public class IosSplashScreen extends Screen {
    private static final long FADE_IN_MS = 500;
    private static final long HOLD_MS = 1400;
    private static final long FADE_OUT_MS = 500;

    private final Screen next;
    private final long start = System.currentTimeMillis();
    private boolean finished = false;

    public IosSplashScreen(Screen next) {
        super(Text.literal("Elara"));
        this.next = next;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long t = System.currentTimeMillis() - start;
        if (t >= FADE_IN_MS + HOLD_MS + FADE_OUT_MS) {
            finish();
            return;
        }

        float alpha;
        if (t < FADE_IN_MS) {
            alpha = t / (float) FADE_IN_MS;
        } else if (t < FADE_IN_MS + HOLD_MS) {
            alpha = 1f;
        } else {
            alpha = 1f - (t - FADE_IN_MS - HOLD_MS) / (float) FADE_OUT_MS;
        }

        ctx.fill(0, 0, width, height, 0xFF000000);

        int cx = width / 2;
        int cy = height / 2 - 12;
        AppleLogo.draw(ctx, cx, cy, 64, IosStyle.withAlpha(IosStyle.TEXT, alpha));
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Elara Client"), cx, cy + 46,
                IosStyle.withAlpha(IosStyle.TEXT_DIM, alpha));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        finish();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        finish();
        return true;
    }

    private void finish() {
        if (finished) return;
        finished = true;
        MinecraftClient.getInstance().setScreen(next);
    }
}
