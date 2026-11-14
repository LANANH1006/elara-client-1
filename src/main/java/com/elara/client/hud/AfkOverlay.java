package com.elara.client.hud;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.i18n.Lang;
import com.elara.client.ui.IosStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.time.LocalTime;

/**
 * Lớp phủ tối khi bật chế độ AFK tiết kiệm pin: đồng hồ lớn, trạng thái và FPS hiện tại.
 * Chỉ vẽ, không chặn thao tác, nên mở menu bằng phím vẫn được.
 */
public final class AfkOverlay {

    private AfkOverlay() {}

    public static void render(DrawContext ctx) {
        if (!ElaraConfig.isOn(Feature.AFK_SAVER)) return;
        MinecraftClient c = MinecraftClient.getInstance();
        int w = c.getWindow().getScaledWidth();
        int h = c.getWindow().getScaledHeight();

        ctx.fill(0, 0, w, h, 0xE6000000);

        LocalTime now = LocalTime.now();
        String clock = String.format("%02d:%02d", now.getHour(), now.getMinute());
        ctx.getMatrices().push();
        ctx.getMatrices().translate(w / 2f, h / 2f - 12f, 0f);
        ctx.getMatrices().scale(3f, 3f, 1f);
        ctx.drawCenteredTextWithShadow(c.textRenderer, Text.literal(clock), 0, 0, IosStyle.TEXT);
        ctx.getMatrices().pop();

        String status = Lang.get("afk.status") + " · " + c.getCurrentFps() + " FPS";
        ctx.drawCenteredTextWithShadow(c.textRenderer, Text.literal(status), w / 2, h / 2 + 18, IosStyle.TEXT_DIM);
        ctx.drawCenteredTextWithShadow(c.textRenderer, Text.literal(Lang.get("afk.hint")),
                w / 2, h / 2 + 30, IosStyle.TEXT_DIM);
    }
}
