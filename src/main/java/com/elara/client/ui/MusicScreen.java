package com.elara.client.ui;

import com.elara.client.i18n.Lang;
import com.elara.client.music.MusicPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

/** Bảng điều khiển trình phát nhạc: danh sách bài, phát/dừng, chuyển bài, thanh tua, lời. */
public class MusicScreen extends Screen {
    private static final int ROWS = 5;
    private static final int ROW0 = 30;
    private static final int ROW_H = 18;
    private static final int CTRL_Y = 126;
    private static final int SEEK_Y = 150;
    private static final int TIME_Y = 160;
    private static final int LYRIC_Y = 172;
    private static final int RESCAN_Y = 186;

    private final Screen parent;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int scroll;
    private boolean draggingSeek;

    public MusicScreen(Screen parent) {
        super(Text.literal("Music"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MusicPlayer.rescan();
        pw = Math.min(width - 16, 300);
        ph = 214;
        px = (width - pw) / 2;
        py = Math.max(8, (height - ph) / 2);
    }

    private int cx() { return px + pw / 2; }
    private int rowY(int i) { return py + ROW0 + i * ROW_H; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        IosStyle.glassPanel(ctx, px, py, pw, ph, 14);
        ctx.drawText(textRenderer, Text.literal(Lang.get("music.title")), px + 12, py + 10, IosStyle.TEXT, false);

        List<Path> tracks = MusicPlayer.tracks();
        if (tracks.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("music.empty")),
                    cx(), rowY(0) + 4, IosStyle.TEXT_DIM);
        }
        scroll = Math.max(0, Math.min(scroll, tracks.size() - ROWS));
        for (int i = 0; i < ROWS && scroll + i < tracks.size(); i++) {
            int idx = scroll + i;
            int y = rowY(i);
            boolean current = idx == MusicPlayer.index();
            if (current) {
                IosStyle.rounded(ctx, px + 8, y, pw - 16, 16, 6, IosStyle.TILE);
            }
            String name = trim(MusicPlayer.name(tracks.get(idx)), pw - 40);
            ctx.drawText(textRenderer, Text.literal(name), px + 14, y + 4,
                    current ? IosStyle.ACCENT : IosStyle.TEXT, false);
        }

        drawControls(ctx);
        drawSeek(ctx);

        String time = fmt(MusicPlayer.positionMs()) + " / " + fmt(MusicPlayer.lengthMs());
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(time), cx(), py + TIME_Y, IosStyle.TEXT_DIM);
        String lyric = MusicPlayer.lyricAt(MusicPlayer.positionMs());
        if (!lyric.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(trim(lyric, pw - 24)),
                    cx(), py + LYRIC_Y, IosStyle.ACCENT);
        }

        IosStyle.rounded(ctx, px + 12, py + RESCAN_Y, 80, 18, 9, IosStyle.TILE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("music.rescan")),
                px + 52, py + RESCAN_Y + 5, IosStyle.TEXT);
        String note = MusicPlayer.status().isEmpty() ? Lang.get("music.hint") : MusicPlayer.status();
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(trim(note, pw - 24)),
                cx(), py + ph - 10, IosStyle.TEXT_DIM);
    }

    private void drawControls(DrawContext ctx) {
        int y = py + CTRL_Y;
        drawBtn(ctx, cx() - 54, y, "<<");
        drawBtn(ctx, cx() - 18, y, MusicPlayer.isPlaying() ? "II" : ">");
        drawBtn(ctx, cx() + 18, y, ">>");
    }

    private void drawBtn(DrawContext ctx, int x, int y, String label) {
        IosStyle.rounded(ctx, x, y, 36, 18, 9, IosStyle.TILE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + 18, y + 5, IosStyle.TEXT);
    }

    private void drawSeek(DrawContext ctx) {
        int tx = px + 12;
        int tw = pw - 24;
        int ty = py + SEEK_Y + 4;
        IosStyle.rounded(ctx, tx, ty, tw, 6, 3, IosStyle.GRAY);
        float frac = (float) MusicPlayer.progress();
        IosStyle.rounded(ctx, tx, ty, Math.max(6, Math.round(tw * frac)), 6, 3, IosStyle.ACCENT);
    }

    private static String fmt(long ms) {
        long s = ms / 1000L;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static String trim(String s, int maxPx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.textRenderer.getWidth(s) <= maxPx) return s;
        String out = s;
        while (out.length() > 1 && client.textRenderer.getWidth(out + "..") > maxPx) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        List<Path> tracks = MusicPlayer.tracks();
        for (int i = 0; i < ROWS && scroll + i < tracks.size(); i++) {
            if (inside(mx, my, px + 8, rowY(i), pw - 16, ROW_H - 2)) {
                MusicPlayer.play(scroll + i);
                return true;
            }
        }
        int y = py + CTRL_Y;
        if (inside(mx, my, cx() - 54, y, 36, 18)) {
            MusicPlayer.prev();
            return true;
        }
        if (inside(mx, my, cx() - 18, y, 36, 18)) {
            MusicPlayer.toggle();
            return true;
        }
        if (inside(mx, my, cx() + 18, y, 36, 18)) {
            MusicPlayer.next();
            return true;
        }
        if (inside(mx, my, px + 12, py + SEEK_Y, pw - 24, 14)) {
            draggingSeek = true;
            seekTo(mx);
            return true;
        }
        if (inside(mx, my, px + 12, py + RESCAN_Y, 80, 18)) {
            MusicPlayer.rescan();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void seekTo(double mx) {
        double frac = (mx - (px + 12)) / (double) (pw - 24);
        MusicPlayer.seek(frac);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSeek) {
            seekTo(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingSeek) {
            draggingSeek = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        scroll -= (int) Math.signum(verticalAmount);
        return true;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
