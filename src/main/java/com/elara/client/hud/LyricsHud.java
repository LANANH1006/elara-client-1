package com.elara.client.hud;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.music.MusicPlayer;
import com.elara.client.ui.IosStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Hiện dòng lời đang hát ngay dưới Dynamic Island, đồng bộ theo file .lrc */
public final class LyricsHud {

    private LyricsHud() {}

    public static void render(DrawContext ctx) {
        if (!ElaraConfig.isOn(Feature.LYRICS_ISLAND)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null || client.options.hudHidden) return;

        String line = MusicPlayer.lyricAt(MusicPlayer.positionMs());
        if (line == null || line.isBlank()) return;

        TextRenderer tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();
        int w = tr.getWidth(line) + 16;
        int y = ElaraConfig.islandOffsetY() + ElaraConfig.islandHeight() + 6;
        IosStyle.rounded(ctx, sw / 2 - w / 2, y, w, 14, 7, 0x99000000);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(line), sw / 2, y + 3, IosStyle.TEXT);
    }
}
