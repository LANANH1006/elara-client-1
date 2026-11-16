package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.i18n.Lang;
import com.elara.client.music.MusicPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

/**
 * Bảng nhạc: ô dán link (mp3, wav, ogg trực tiếp), danh sách trong thư mục, lịch sử link,
 * phát, dừng, chuyển bài, thanh tua, âm lượng và dòng lời đang hát.
 */
public class MusicScreen extends Screen {
    private static final int LIST_ROWS = 3;
    private static final int HIST_ROWS = 3;
    private static final int ROW_H = 13;

    private final Screen parent;
    private final TextBox url = new TextBox(300);
    private int px;
    private int py;
    private int pw;
    private int ph;
    private boolean dragSeek;
    private boolean dragVol;

    public MusicScreen(Screen parent) {
        super(Text.literal("Music"));
        this.parent = parent;
        this.url.setText(ElaraConfig.musicUrl());
    }

    @Override
    protected void init() {
        MusicPlayer.rescan();
        pw = Math.min(width - 16, 300);
        ph = 236;
        px = (width - pw) / 2;
        py = Math.max(6, (height - ph) / 2);
    }

    // ---------- toạ độ ----------

    private int urlX() { return px + 12; }
    private int urlY() { return py + 26; }
    private int urlW() { return pw - 128; }
    private int pasteX() { return px + pw - 112; }
    private int playUrlX() { return px + pw - 44; }
    private int rescanX() { return px + pw - 64; }
    private int listY(int i) { return py + 70 + i * ROW_H; }
    private int histY(int i) { return py + 122 + i * ROW_H; }
    private int ctrlY() { return py + 164; }
    private int seekX() { return px + 12; }
    private int seekW() { return pw - 24; }
    private int seekY() { return py + 196; }
    private int volY() { return py + 216; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        IosStyle.glassPanel(ctx, px, py, pw, ph, 14);
        ctx.drawText(textRenderer, Text.literal(Lang.get("music.title")), px + 12, py + 9, IosStyle.TEXT, false);
        button(ctx, rescanX(), py + 7, 52, 14, Lang.get("music.rescan"), mouseX, mouseY);

        // Ô link: chữ gợi ý khi trống, con trỏ khi đang nhập
        boolean empty = url.text().isEmpty() && !url.focused();
        IosStyle.rounded(ctx, urlX(), urlY(), urlW(), 16, 5, 0x55000000);
        String shown = empty ? Lang.get("snd.url") : url.text() + (url.focused() ? url.caret() : "");
        ctx.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(shown, urlW() - 8)),
                urlX() + 4, urlY() + 4, empty ? IosStyle.TEXT_DIM : IosStyle.TEXT, false);
        button(ctx, pasteX(), urlY(), 56, 16, Lang.get("snd.paste"), mouseX, mouseY);
        button(ctx, playUrlX(), urlY(), 36, 16, Lang.get("snd.play"), mouseX, mouseY);

        // Trạng thái: đang tải, lỗi, hoặc tên bài đang phát
        String st = MusicPlayer.isLoading() ? Lang.get("snd.loading") : MusicPlayer.status();
        if (st.isEmpty() && MusicPlayer.isPlaying()) st = MusicPlayer.title();
        ctx.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(st, pw - 24)),
                px + 12, py + 46, IosStyle.TEXT_DIM, false);

        // Danh sách trong thư mục
        ctx.drawText(textRenderer, Text.literal(Lang.get("snd.local")), px + 12, py + 60, IosStyle.TEXT_DIM, false);
        List<Path> tracks = MusicPlayer.tracks();
        if (tracks.isEmpty()) {
            ctx.drawText(textRenderer, Text.literal(Lang.get("music.empty")), px + 12, listY(0), IosStyle.TEXT_DIM, false);
        }
        for (int i = 0; i < Math.min(LIST_ROWS, tracks.size()); i++) {
            row(ctx, listY(i), MusicPlayer.name(tracks.get(i)), MusicPlayer.index() == i, mouseX, mouseY);
        }

        // Lịch sử link đã phát
        ctx.drawText(textRenderer, Text.literal(Lang.get("snd.history")), px + 12, py + 112, IosStyle.TEXT_DIM, false);
        List<String> hist = ElaraConfig.musicHistory();
        if (hist.isEmpty()) {
            ctx.drawText(textRenderer, Text.literal("—"), px + 12, histY(0), IosStyle.TEXT_DIM, false);
        }
        for (int i = 0; i < Math.min(HIST_ROWS, hist.size()); i++) {
            row(ctx, histY(i), hist.get(i), false, mouseX, mouseY);
        }

        // Điều khiển
        button(ctx, px + 12, ctrlY(), 56, 16, Lang.get("snd.prev"), mouseX, mouseY);
        button(ctx, px + pw / 2 - 28, ctrlY(), 56, 16,
                MusicPlayer.isPlaying() ? Lang.get("snd.pause") : Lang.get("snd.play"), mouseX, mouseY);
        button(ctx, px + pw - 68, ctrlY(), 56, 16, Lang.get("snd.next"), mouseX, mouseY);

        // Thanh tua và thời gian
        bar(ctx, seekX(), seekY(), seekW(), MusicPlayer.progress());
        String time = clock(MusicPlayer.positionMs()) + " / " + clock(MusicPlayer.lengthMs());
        ctx.drawText(textRenderer, Text.literal(time), px + 12, py + 186, IosStyle.TEXT_DIM, false);

        // Âm lượng
        ctx.drawText(textRenderer, Text.literal(Lang.get("snd.volume") + " " + MusicPlayer.volume() + "%"),
                px + 12, py + 204, IosStyle.TEXT_DIM, false);
        bar(ctx, seekX(), volY(), seekW(), MusicPlayer.volume() / 100.0);

        // Dòng lời đang hát, căn giữa
        String lyric = MusicPlayer.lyricAt(MusicPlayer.positionMs());
        if (!lyric.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(textRenderer.trimToWidth(lyric, pw - 16)), px + pw / 2, py + 224, IosStyle.TEXT);
        }
    }

    private void button(DrawContext ctx, int x, int y, int w, int h, String label, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h);
        IosStyle.rounded(ctx, x, y, w, h, 5, hover ? 0xFF4A4A4F : 0xFF3A3A3C);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + w / 2, y + (h - 8) / 2,
                IosStyle.TEXT);
    }

    private void row(DrawContext ctx, int y, String label, boolean active, int mx, int my) {
        boolean hover = inside(mx, my, px + 8, y, pw - 16, ROW_H);
        if (active || hover) {
            IosStyle.rounded(ctx, px + 8, y, pw - 16, ROW_H, 4, active ? 0x40FFFFFF : 0x20FFFFFF);
        }
        ctx.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(label, pw - 28)),
                px + 12, y + 3, active ? IosStyle.accent() : IosStyle.TEXT, false);
    }

    private void bar(DrawContext ctx, int x, int y, int w, double frac) {
        IosStyle.rounded(ctx, x, y, w, 4, 2, 0x55FFFFFF);
        int fill = (int) Math.round(w * Math.max(0, Math.min(1, frac)));
        if (fill > 0) IosStyle.rounded(ctx, x, y, fill, 4, 2, IosStyle.accent());
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms / 1000L);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------- chuột ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        MinecraftClient client = MinecraftClient.getInstance();

        if (inside(mx, my, rescanX(), py + 7, 52, 14)) {
            MusicPlayer.rescan();
            return true;
        }
        if (inside(mx, my, urlX(), urlY(), urlW(), 16)) {
            url.setFocused(true);
            return true;
        }
        url.setFocused(false);
        if (inside(mx, my, pasteX(), urlY(), 56, 16)) {
            url.setText(client.keyboard.getClipboard());
            url.setFocused(true);
            return true;
        }
        if (inside(mx, my, playUrlX(), urlY(), 36, 16)) {
            MusicPlayer.playUrl(url.text());
            return true;
        }

        List<Path> tracks = MusicPlayer.tracks();
        for (int i = 0; i < Math.min(LIST_ROWS, tracks.size()); i++) {
            if (inside(mx, my, px + 8, listY(i), pw - 16, ROW_H)) {
                MusicPlayer.play(i);
                return true;
            }
        }
        List<String> hist = ElaraConfig.musicHistory();
        for (int i = 0; i < Math.min(HIST_ROWS, hist.size()); i++) {
            if (inside(mx, my, px + 8, histY(i), pw - 16, ROW_H)) {
                url.setText(hist.get(i));
                MusicPlayer.playUrl(hist.get(i));
                return true;
            }
        }

        if (inside(mx, my, px + 12, ctrlY(), 56, 16)) {
            MusicPlayer.prev();
            return true;
        }
        if (inside(mx, my, px + pw / 2 - 28, ctrlY(), 56, 16)) {
            MusicPlayer.toggle();
            return true;
        }
        if (inside(mx, my, px + pw - 68, ctrlY(), 56, 16)) {
            MusicPlayer.next();
            return true;
        }
        if (inside(mx, my, seekX(), seekY() - 4, seekW(), 12)) {
            dragSeek = true;
            applySeek(mx);
            return true;
        }
        if (inside(mx, my, seekX(), volY() - 4, seekW(), 12)) {
            dragVol = true;
            applyVol(mx);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragSeek) applySeek(mx);
        if (dragVol) applyVol(mx);
        return dragSeek || dragVol || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragSeek = false;
        dragVol = false;
        return super.mouseReleased(mx, my, button);
    }

    private void applySeek(double mx) {
        MusicPlayer.seek((mx - seekX()) / seekW());
    }

    private void applyVol(double mx) {
        MusicPlayer.setVolume((int) Math.round((mx - seekX()) / seekW() * 100));
    }

    // ---------- bàn phím ----------

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (url.focused()) {
            url.append(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (url.focused()) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                url.backspace();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                MusicPlayer.playUrl(url.text());
                url.setFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                url.appendAll(MinecraftClient.getInstance().keyboard.getClipboard());
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        ElaraConfig.setMusicUrl(url.text());
        MinecraftClient.getInstance().setScreen(parent);
    }
}
