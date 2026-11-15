package com.elara.client.hud;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.music.MusicPlayer;
import com.elara.client.ui.IosStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/**
 * Lời bài hát đồng bộ theo file .lrc.
 * Hai chế độ: dòng nhỏ dưới Dynamic Island (Lời trên đảo), và lời lớn mờ dần ở phía trên bầu trời
 * (Lời trên bầu trời). Lời trên bầu trời là lớp vẽ trên màn hình, không phải chữ 3D trong thế giới.
 */
public final class LyricsHud {
    /** Thời gian một dòng lời hiện dần vào, tính bằng ms */
    private static final long FADE_IN_MS = 500L;

    private LyricsHud() {}

    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null || client.options.hudHidden) return;

        if (ElaraConfig.isOn(Feature.LYRICS_SKY)) renderSky(ctx, client);
        if (!ElaraConfig.isOn(Feature.LYRICS_ISLAND)) return;

        String line = MusicPlayer.lyricAt(MusicPlayer.positionMs());
        if (line == null || line.isBlank()) return;

        TextRenderer tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();
        int w = tr.getWidth(line) + 16;
        int y = ElaraConfig.islandOffsetY() + ElaraConfig.islandHeight() + 6;
        IosStyle.rounded(ctx, sw / 2 - w / 2, y, w, 14, 7, 0x99000000);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(line), sw / 2, y + 3, IosStyle.TEXT);
    }

    /** Lời lớn, phát sáng nhẹ, hiện dần theo thời gian của dòng. Chỉ hiện khi đang phát nhạc. */
    private static void renderSky(DrawContext ctx, MinecraftClient client) {
        if (!MusicPlayer.isPlaying()) return;
        long pos = MusicPlayer.positionMs();
        String line = MusicPlayer.lyricAt(pos);
        if (line == null || line.isBlank()) return;

        long start = MusicPlayer.lyricStartMs(pos);
        float fade = start < 0 ? 1f : Math.min(1f, (pos - start) / (float) FADE_IN_MS);
        int alpha = Math.round(fade * 255f);
        if (alpha <= 0) return;

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        TextRenderer tr = client.textRenderer;
        Text text = Text.literal(line);

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        // Đặt cao, gần một phần năm đỉnh màn hình, và phóng to hai lần
        ms.translate(sw / 2.0, sh * 0.18, 0.0);
        ms.scale(2.0f, 2.0f, 1.0f);
        int glow = ((alpha / 3) << 24) | 0x9FD8FF;
        ctx.drawCenteredTextWithShadow(tr, text, -1, 0, glow);
        ctx.drawCenteredTextWithShadow(tr, text, 1, 0, glow);
        ctx.drawCenteredTextWithShadow(tr, text, 0, -1, glow);
        ctx.drawCenteredTextWithShadow(tr, text, 0, 1, glow);
        ctx.drawCenteredTextWithShadow(tr, text, 0, 0, (alpha << 24) | 0xFFFFFF);
        ms.pop();
    }
}
