package com.elara.client.ui;

import com.elara.client.core.Guard;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.core.LagFix;
import com.elara.client.fx.KnifeAnim;
import com.elara.client.i18n.Lang;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bàn phím ảo kính mờ và thanh nút nhanh phía trên ô chat. Vẽ bằng IosStyle trong game,
 * không phụ thuộc bàn phím Android.
 *
 * Ký tự được gửi vào chính màn hình chat qua charTyped và keyPressed, nên chat xử lý như gõ thật.
 * Tắt tính năng Chat Touch (tab Tiện ích) thì không vẽ và không nhận chạm.
 * Thanh nút: Menu Elara, đổi góc nhìn, múa dao, bật/tắt Fix Lag tối đa, và nút mở bàn phím.
 */
public final class ChatKeyboard {
    private static final int KEY_H = 14;
    private static final int GAP = 2;
    private static final int PAD = 3;
    private static final int BAR_H = 16;
    /** Chiều cao ô chat của ChatScreen, nằm sát đáy màn hình */
    private static final int FIELD_H = 12;
    private static final int EDGE = 4;
    private static final int ROWS = 6;

    private enum Kind { CHAR, SHIFT, BACK, SPACE, PASTE, ENTER, HIDE }

    private enum Act { MENU, SPIN, LAG, KEYS }

    /** Mô tả một phím trước khi đặt vào hàng */
    private static final class Spec {
        final String label;
        final char ch;
        final Kind kind;
        final int units;

        Spec(String label, char ch, Kind kind, int units) {
            this.label = label;
            this.ch = ch;
            this.kind = kind;
            this.units = units;
        }
    }

    /** Một phím đã đặt vị trí. Chữ được tạo sẵn ở hai dạng hoa và thường, để không tạo đối tượng mỗi khung. */
    private static final class Key {
        final int x;
        final int y;
        final int w;
        final char ch;
        final Kind kind;
        final Text upper;
        final Text lower;

        Key(int x, int y, int w, char ch, Kind kind, String label) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.ch = ch;
            this.kind = kind;
            this.upper = Text.literal(label);
            this.lower = Text.literal(label.toLowerCase(Locale.ROOT));
        }
    }

    private static final class Btn {
        final int x;
        final int y;
        final int w;
        final Act act;
        final Text text;

        Btn(int x, int y, int w, Act act, Text text) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.act = act;
            this.text = text;
        }
    }

    private static final List<Key> KEYS = new ArrayList<>();
    private static final List<Btn> BUTTONS = new ArrayList<>();
    private static int panelX;
    private static int panelY;
    private static int panelW;
    private static int panelH;
    private static int barY;
    private static int builtW = -1;
    private static int builtH = -1;
    private static boolean open;
    private static boolean shift;
    private static boolean broken;
    private static Screen attached;

    private ChatKeyboard() {}

    /** Gắn sự kiện vẽ và nhận chạm cho một màn hình chat. Gọi một lần cho mỗi màn hình. */
    public static void attach(Screen screen) {
        if (screen == attached) return;
        attached = screen;
        ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> render(ctx, mx, my));
        ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> click(s, mx, my, button));
    }

    // ---------- Vẽ ----------

    private static void render(DrawContext ctx, int mouseX, int mouseY) {
        if (broken || !ElaraConfig.isOn(Feature.CHAT_TOUCH)) return;
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            int w = c.getWindow().getScaledWidth();
            int h = c.getWindow().getScaledHeight();
            ensureLayout(w, h, c);
            for (Btn b : BUTTONS) {
                boolean on = (b.act == Act.KEYS && open) || (b.act == Act.LAG && LagFix.maxOn());
                drawButton(ctx, c, b, on, inside(b.x, barY, b.w, BAR_H, mouseX, mouseY));
            }
            if (!open) return;
            IosStyle.glassPanel(ctx, panelX, panelY, panelW, panelH, 6);
            for (Key k : KEYS) {
                drawKey(ctx, c, k, inside(k.x, k.y, k.w, KEY_H, mouseX, mouseY));
            }
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            broken = true;
            Fault.fail("ui.chatkeys", t);
        }
    }

    private static void drawButton(DrawContext ctx, MinecraftClient c, Btn b, boolean on, boolean hover) {
        int bg = on ? IosStyle.ACCENT : 0x9E3A3A3C;
        if (hover) bg = IosStyle.lerpColor(bg, 0xFFFFFFFF, 0.2f);
        IosStyle.rounded(ctx, b.x, barY, b.w, BAR_H, 4, bg);
        ctx.drawCenteredTextWithShadow(c.textRenderer, b.text, b.x + b.w / 2, barY + (BAR_H - 8) / 2 + 1,
                IosStyle.TEXT);
    }

    private static void drawKey(DrawContext ctx, MinecraftClient c, Key k, boolean hover) {
        boolean active = k.kind == Kind.SHIFT && shift;
        int bg;
        if (active) {
            bg = IosStyle.ACCENT;
        } else {
            bg = k.kind == Kind.CHAR ? 0xB0505055 : 0xB03A3A3C;
        }
        if (hover) bg = IosStyle.lerpColor(bg, 0xFFFFFFFF, 0.25f);
        IosStyle.rounded(ctx, k.x, k.y, k.w, KEY_H, 3, bg);
        Text text = (k.kind == Kind.CHAR && !shift) ? k.lower : k.upper;
        ctx.drawCenteredTextWithShadow(c.textRenderer, text, k.x + k.w / 2, k.y + (KEY_H - 8) / 2 + 1,
                IosStyle.TEXT);
    }

    /** Đặt lại vị trí các phím và nút khi kích thước cửa sổ đổi */
    private static void ensureLayout(int w, int h, MinecraftClient c) {
        if (w == builtW && h == builtH) return;
        builtW = w;
        builtH = h;
        KEYS.clear();
        BUTTONS.clear();

        barY = h - FIELD_H - EDGE - BAR_H;
        int panelBottom = barY - EDGE;
        int rowsH = ROWS * KEY_H + (ROWS - 1) * GAP;
        panelH = rowsH + 2 * PAD;
        panelY = panelBottom - panelH;
        panelX = EDGE;
        panelW = w - 2 * EDGE;

        int x = EDGE;
        x = addButton(c, x, Act.MENU, Lang.get("chatkb.menu"));
        x = addButton(c, x, Act.SPIN, Lang.get("chatkb.spin"));
        addButton(c, x, Act.LAG, Lang.get("chatkb.lag"));
        String keysLabel = Lang.get("chatkb.keys");
        int kw = c.textRenderer.getWidth(keysLabel) + 12;
        BUTTONS.add(new Btn(w - EDGE - kw, barY, kw, Act.KEYS, Text.literal(keysLabel)));

        int left = panelX + PAD;
        int width = panelW - 2 * PAD;
        int y = panelY + PAD;
        addRow(y, left, width, chars("1234567890"));
        y += KEY_H + GAP;
        addRow(y, left, width, chars("QWERTYUIOP"));
        y += KEY_H + GAP;
        addRow(y, left, width, chars("ASDFGHJKL"));
        y += KEY_H + GAP;
        List<Spec> r3 = new ArrayList<>();
        r3.add(new Spec(Lang.get("chatkb.shift"), ' ', Kind.SHIFT, 2));
        r3.addAll(chars("ZXCVBNM"));
        r3.add(new Spec(Lang.get("chatkb.back"), ' ', Kind.BACK, 2));
        addRow(y, left, width, r3);
        y += KEY_H + GAP;
        List<Spec> r4 = chars("/-_.@!?");
        r4.add(new Spec(Lang.get("chatkb.paste"), ' ', Kind.PASTE, 2));
        r4.add(new Spec(Lang.get("chatkb.enter"), ' ', Kind.ENTER, 2));
        addRow(y, left, width, r4);
        y += KEY_H + GAP;
        List<Spec> r5 = new ArrayList<>();
        r5.add(new Spec(Lang.get("chatkb.space"), ' ', Kind.SPACE, 6));
        r5.add(new Spec(Lang.get("chatkb.hide"), ' ', Kind.HIDE, 2));
        addRow(y, left, width, r5);
    }

    private static int addButton(MinecraftClient c, int x, Act act, String label) {
        int bw = c.textRenderer.getWidth(label) + 12;
        BUTTONS.add(new Btn(x, barY, bw, act, Text.literal(label)));
        return x + bw + GAP;
    }

    /** Ký tự thường thành danh sách phím, chữ cái lưu dạng hoa */
    private static List<Spec> chars(String s) {
        List<Spec> out = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            out.add(new Spec(String.valueOf(Character.toUpperCase(ch)), ch, Kind.CHAR, 1));
        }
        return out;
    }

    /** Đặt một hàng phím, các phím chia đều chiều rộng theo số đơn vị */
    private static void addRow(int y, int left, int width, List<Spec> specs) {
        int total = 0;
        for (Spec s : specs) total += s.units;
        int gaps = (specs.size() - 1) * GAP;
        float unit = (width - gaps) / (float) total;
        float x = left;
        for (int i = 0; i < specs.size(); i++) {
            Spec s = specs.get(i);
            int kw = Math.round(unit * s.units);
            if (i == specs.size() - 1) kw = left + width - Math.round(x);
            KEYS.add(new Key(Math.round(x), y, kw, s.ch, s.kind, s.label));
            x += kw + GAP;
        }
    }

    // ---------- Chạm ----------

    /** Trả về false để chặn chạm (không chuyển cho ô chat), true để cho game xử lý bình thường */
    private static boolean click(Screen screen, double mx, double my, int button) {
        if (broken || button != 0 || !ElaraConfig.isOn(Feature.CHAT_TOUCH)) return true;
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            for (Btn b : BUTTONS) {
                if (inside(b.x, barY, b.w, BAR_H, mx, my)) {
                    run(b.act, c, screen);
                    return false;
                }
            }
            if (!open || !inside(panelX, panelY, panelW, panelH, mx, my)) return true;
            for (Key k : KEYS) {
                if (inside(k.x, k.y, k.w, KEY_H, mx, my)) {
                    press(k, c, screen);
                    break;
                }
            }
            return false;
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            broken = true;
            Fault.fail("ui.chatkeys", t);
            return true;
        }
    }

    private static void run(Act act, MinecraftClient c, Screen screen) {
        switch (act) {
            case MENU -> c.setScreen(new ElaraMenuScreen(screen));
            case SPIN -> KnifeAnim.inspect(c);
            case LAG -> LagFix.toggleMax();
            case KEYS -> open = !open;
        }
    }

    private static void press(Key k, MinecraftClient c, Screen screen) {
        switch (k.kind) {
            case CHAR -> {
                char out = (shift && Character.isLetter(k.ch)) ? k.ch : Character.toLowerCase(k.ch);
                screen.charTyped(out, 0);
                // Shift chỉ áp cho một ký tự, rồi tự tắt như bàn phím điện thoại
                shift = false;
            }
            case SPACE -> screen.charTyped(' ', 0);
            case SHIFT -> shift = !shift;
            case BACK -> screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            case ENTER -> screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            case PASTE -> paste(c, screen);
            case HIDE -> open = false;
        }
    }

    private static void paste(MinecraftClient c, Screen screen) {
        String s;
        try {
            s = c.keyboard.getClipboard();
        } catch (RuntimeException ex) {
            return;
        }
        if (s == null) return;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 32 && ch != 127) screen.charTyped(ch, 0);
        }
    }

    private static boolean inside(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
