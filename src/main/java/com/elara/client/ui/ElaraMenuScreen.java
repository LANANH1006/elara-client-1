package com.elara.client.ui;

import com.elara.client.ElaraClient;
import com.elara.client.core.AuthorGuard;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.core.Fault;
import com.elara.client.core.MemoryTools;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * Menu Elara kiểu iOS: thanh tab viên thuốc, lưới thẻ 2 cột, cuộn bằng chuột hoặc vuốt.
 * Khi mở thì phóng to nhẹ và hiện dần, khi đóng thì mờ đi rồi mới thoát.
 */
public class ElaraMenuScreen extends Screen {
    private static final Feature.Group[] TABS = Feature.Group.values();
    private static final int PAD = 8;
    private static final int PILL_H = 18;
    private static final int CARD_H = 46;
    private static final int CARD_GAP = 6;
    private static final int EXTRA_H = 28;
    private static final int DRAG_SLOP = 4;
    private static final int SW_W = 26;
    private static final int SW_H = 14;
    private static final int SL_W = 16;
    private static final int[] SWATCHES = {
            0xFF0A84FF, 0xFF30D158, 0xFFFF375F, 0xFFBF5AF2,
            0xFFFF9F0A, 0xFFFF8FB1, 0xFF64D2FF, 0xFFFFD60A
    };

    private final Screen parent;
    private Feature.Group tab = Feature.Group.FIX_LAG;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int scroll = 0;
    private double scrollAcc = 0;
    private int contentH = 0;
    private boolean pressing = false;
    private boolean dragged = false;
    private double pressX;
    private double pressY;
    private int draggingSlider = -1;
    private String gcMessage = null;

    public ElaraMenuScreen(Screen parent) {
        super(Text.literal("Elara Client"));
        this.parent = parent;
        IosStyle.beginOpen();
        // Mở lại đúng tab và vị trí cuộn lần trước
        tab = TABS[Math.max(0, Math.min(TABS.length - 1, ElaraConfig.menuTab()))];
        scroll = Math.max(0, ElaraConfig.menuScroll());
    }

    /** Chỉ số của tab đang mở trong TABS, để lưu vào cấu hình */
    private int menuIndex() {
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i] == tab) return i;
        }
        return 0;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 12, 380);
        ph = Math.min(height - 12, 330);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    // ---------- bố cục ----------

    private int tabSlot() { return (pw - PAD * 2) / TABS.length; }
    private int tabX(int i) { return px + PAD + i * tabSlot(); }
    private int tabY() { return py + 8; }
    private int contentTop() { return py + 46; }
    private int contentBottom() { return py + ph - 22; }
    private int viewH() { return contentBottom() - contentTop(); }
    private int cardW() { return (pw - PAD * 2 - CARD_GAP) / 2; }
    private int cardX(int col) { return px + PAD + col * (cardW() + CARD_GAP); }
    private int cardY(int row) { return contentTop() - scroll + row * (CARD_H + CARD_GAP); }
    private int switchX(int cx) { return cx + cardW() - 8 - SW_W; }
    private int sliderX(int cx) { return switchX(cx) - 6 - SL_W; }

    /** Đầu vùng của hàng phụ thứ k (nút, thanh trượt, bảng màu) nằm dưới các thẻ */
    private int extraY(int n, int k) {
        return cardY(0) + ((n + 1) / 2) * (CARD_H + CARD_GAP) + 6 + k * EXTRA_H + 4;
    }


    /** Số hàng phụ ở cuối mỗi tab */
    private static int extrasFor(Feature.Group g) {
        return switch (g) {
            case FIX_LAG -> 2;
            case MISC -> 9;
            case BUILDER -> 1;
            default -> 0;
        };
    }

    private int contentHeight() {
        if (tab == Feature.Group.PACK) {
            return EXTRA_H * 3;
        }
        int n = Feature.inGroup(tab).size();
        int h = ((n + 1) / 2) * (CARD_H + CARD_GAP);
        int extras = extrasFor(tab);
        return extras > 0 ? h + 6 + extras * EXTRA_H : h;
    }

    private int clampScroll(int v) {
        return Math.max(0, Math.min(v, Math.max(0, contentH - viewH())));
    }

    // ---------- vẽ ----------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (IosStyle.isClosing() && IosStyle.closeDone()) {
            IosStyle.setFade(1f);
            MinecraftClient.getInstance().setScreen(parent);
            return;
        }
        renderBackground(ctx, mouseX, mouseY, delta);

        float open = IosStyle.openFactor();
        float s = 0.90f + 0.10f * open;
        float cx = px + pw / 2f;
        float cy = py + ph / 2f;
        IosStyle.setFade(open);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0f);
        ctx.getMatrices().scale(s, s, 1f);
        ctx.getMatrices().translate(-cx, -cy, 0f);
        try {
            drawFrame(ctx);
        } finally {
            ctx.getMatrices().pop();
            IosStyle.setFade(1f);
        }
        drawFaultLine(ctx);
    }

    /** Dòng đỏ nhỏ ở đáy menu: có đoạn bị tắt vì lỗi trong phiên này thì báo cho người chơi biết ngay */
    private void drawFaultLine(DrawContext ctx) {
        String key = Fault.lastKey();
        if (key == null) return;
        int more = Fault.count() - 1;
        String msg = "Lỗi: " + key + (more > 0 ? " (+" + more + ")" : "") + " - xem config/elara/error.log";
        ctx.drawText(textRenderer, Text.literal(trim(msg, width - 12)), 6, height - 12, 0xFFFF4D4D, true);
    }

    private void drawFrame(DrawContext ctx) {
        IosStyle.glassPanel(ctx, px, py, pw, ph, 18);

        // thanh tab viên thuốc
        int slot = tabSlot();
        int accent = IosStyle.accent();
        IosStyle.rounded(ctx, px + PAD - 2, tabY() - 2, slot * TABS.length + 4, PILL_H + 4,
                (PILL_H + 4) / 2, 0x30000000);
        for (int i = 0; i < TABS.length; i++) {
            boolean active = TABS[i] == tab;
            if (active) {
                IosStyle.rounded(ctx, tabX(i) + 1, tabY(), slot - 2, PILL_H, PILL_H / 2,
                        IosStyle.withAlpha(accent, 0.55f));
            }
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(TABS[i].label()),
                    tabX(i) + slot / 2, tabY() + 5,
                    IosStyle.fadeColor(active ? IosStyle.TEXT : IosStyle.TEXT_DIM));
        }

        // bộ đếm tính năng đang bật
        int activeCount = 0;
        int totalCount = 0;
        for (Feature f : Feature.values()) {
            if (f.group == Feature.Group.PACK) continue;
            totalCount++;
            if (ElaraConfig.isOn(f)) activeCount++;
        }
        ctx.drawText(textRenderer, Text.literal(activeCount + " / " + totalCount + " " + Lang.get("ui.active")),
                px + PAD, py + 30, IosStyle.fadeColor(IosStyle.TEXT_DIM), false);

        // nội dung: cắt theo vùng xem, cuộn được
        contentH = contentHeight();
        scroll = clampScroll(scroll);
        ctx.enableScissor(px, contentTop(), px + pw, contentBottom());
        if (tab == Feature.Group.PACK) {
            drawPacks(ctx);
        } else {
            drawCards(ctx);
        }
        ctx.disableScissor();
        drawScrollbar(ctx);

        if (!AuthorGuard.isValid()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("ui.tampered")),
                    px + pw / 2, py + ph - 34, 0xFFFF5555);
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Elara Client · " + ElaraClient.AUTHOR),
                px + pw / 2, py + ph - 12, IosStyle.fadeColor(IosStyle.TEXT_DIM));
    }

    private void drawScrollbar(DrawContext ctx) {
        int view = viewH();
        if (contentH <= view) return;
        int thumbH = Math.max(14, view * view / contentH);
        int range = contentH - view;
        int thumbY = contentTop() + (view - thumbH) * scroll / range;
        IosStyle.rounded(ctx, px + pw - 5, thumbY, 3, thumbH, 1,
                IosStyle.fadeColor(IosStyle.withAlpha(IosStyle.accent(), 0.8f)));
    }

    private void drawCards(DrawContext ctx) {
        List<Feature> items = Feature.inGroup(tab);
        for (int i = 0; i < items.size(); i++) {
            drawCard(ctx, items.get(i), cardX(i % 2), cardY(i / 2));
        }
        int n = items.size();
        if (tab == Feature.Group.FIX_LAG) {
            drawPill(ctx, px + PAD, extraY(n, 0), pw - PAD * 2, 20,
                    gcMessage != null ? gcMessage : Lang.get("ui.gc"));
            ctx.drawText(textRenderer, Text.literal(Lang.get("ui.gc_hint")),
                    px + PAD, extraY(n, 1), IosStyle.fadeColor(IosStyle.TEXT_DIM), false);
        } else if (tab == Feature.Group.MISC) {
            drawMisc(ctx, n);
        } else if (tab == Feature.Group.BUILDER) {
            drawPill(ctx, px + PAD, extraY(n, 0), pw - PAD * 2, 20, Lang.get("builder.open"));
        }
    }

    /** Một thẻ: icon bo vuông, tên, mô tả, nút sliders và công tắc iOS */
    private void drawCard(DrawContext ctx, Feature f, int x, int y) {
        boolean on = ElaraConfig.isOn(f);
        IosStyle.glass(ctx, x, y, cardW(), CARD_H, 16);

        int tileX = x + 8;
        int tileY = y + (CARD_H - 20) / 2;
        IosStyle.rounded(ctx, tileX, tileY, 20, 20, 6,
                IosStyle.fadeColor(on ? IosStyle.accent() : IosStyle.GRAY));
        String letter = f.label().isEmpty() ? "?" : f.label().substring(0, 1).toUpperCase(Locale.ROOT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(letter),
                tileX + 10, tileY + 6, IosStyle.fadeColor(IosStyle.TEXT));

        int textX = x + 34;
        int textW = sliderX(x) - 6 - textX;
        String desc = Lang.get("desc." + f.name().toLowerCase(Locale.ROOT));
        ctx.drawText(textRenderer, Text.literal(trim(f.label(), textW)), textX, y + 8,
                IosStyle.fadeColor(on ? IosStyle.TEXT : IosStyle.TEXT_DIM), false);
        ctx.drawText(textRenderer, Text.literal(trim(desc, textW)), textX, y + 22,
                IosStyle.fadeColor(IosStyle.TEXT_DIM), false);

        // nút sliders: ba vạch có núm, mở bảng cài đặt
        int sx = sliderX(x);
        int sy = y + (CARD_H - 16) / 2;
        for (int i = 0; i < 3; i++) {
            int ly = sy + 3 + i * 5;
            ctx.fill(sx + 2, ly, sx + 14, ly + 1, IosStyle.fadeColor(IosStyle.TEXT_DIM));
            int kx = sx + (i == 0 ? 9 : i == 1 ? 4 : 10);
            IosStyle.rounded(ctx, kx, ly - 1, 3, 3, 1, IosStyle.fadeColor(IosStyle.TEXT));
        }

        IosStyle.toggleSwitch(ctx, switchX(x), y + (CARD_H - SW_H) / 2, on, f, SW_W, SW_H);
    }

    private void drawMisc(DrawContext ctx, int n) {
        ctx.drawText(textRenderer, Text.literal(Lang.get("ui.settings")), px + PAD, extraY(n, 0),
                IosStyle.fadeColor(IosStyle.TEXT_DIM), false);
        String langName = Lang.current() == Lang.Language.VI ? "Tiếng Việt" : "English";
        drawPill(ctx, px + PAD, extraY(n, 1), pw - PAD * 2, 20, Lang.get("ui.language") + ": " + langName);

        ctx.drawText(textRenderer, Text.literal(Lang.get("ui.accent")), px + PAD, extraY(n, 2),
                IosStyle.fadeColor(IosStyle.TEXT), false);
        drawSwatches(ctx, extraY(n, 2));

        int islandH = ElaraConfig.islandHeight();
        int offset = ElaraConfig.islandOffsetY();
        drawSlider(ctx, extraY(n, 3), Lang.get("ui.island_height") + ": " + islandH, islandH, 20, 40);
        drawSlider(ctx, extraY(n, 4), Lang.get("ui.island_offset") + ": " + offset, offset, 0, 40);

        drawPill(ctx, px + PAD, extraY(n, 5), pw - PAD * 2, 20, Lang.get("ui.island_editor"));
        drawPill(ctx, px + PAD, extraY(n, 6), pw - PAD * 2, 20, Lang.get("ui.hud_editor"));
        drawPill(ctx, px + PAD, extraY(n, 7), pw - PAD * 2, 20, Lang.get("music.open"));
        drawPill(ctx, px + PAD, extraY(n, 8), pw - PAD * 2, 20, Lang.get("ui.skin_open"));
    }

    private void drawSwatches(DrawContext ctx, int y) {
        int slot = (pw - PAD * 2) / SWATCHES.length;
        int cur = IosStyle.accent();
        for (int i = 0; i < SWATCHES.length; i++) {
            int cx = px + PAD + i * slot + slot / 2;
            int cy = y + 18;
            if (SWATCHES[i] == cur) {
                IosStyle.rounded(ctx, cx - 8, cy - 8, 16, 16, 8, IosStyle.fadeColor(0xFFFFFFFF));
            }
            IosStyle.rounded(ctx, cx - 6, cy - 6, 12, 12, 6, IosStyle.fadeColor(SWATCHES[i]));
        }
    }

    private void drawSlider(DrawContext ctx, int y, String label, int value, int min, int max) {
        ctx.drawText(textRenderer, Text.literal(label), px + PAD, y, IosStyle.fadeColor(IosStyle.TEXT), false);
        int tw = pw - PAD * 2;
        float frac = Math.max(0f, Math.min(1f, (value - min) / (float) (max - min)));
        IosStyle.rounded(ctx, px + PAD, y + 13, tw, 6, 3, IosStyle.fadeColor(IosStyle.GRAY));
        IosStyle.rounded(ctx, px + PAD, y + 13, Math.max(6, Math.round(tw * frac)), 6, 3,
                IosStyle.fadeColor(IosStyle.accent()));
    }

    private void drawPill(DrawContext ctx, int x, int y, int w, int h, String text) {
        IosStyle.rounded(ctx, x, y, w, h, h / 2, IosStyle.fadeColor(IosStyle.TILE));
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(trim(text, w - 12)),
                x + w / 2, y + (h - 8) / 2, IosStyle.fadeColor(IosStyle.TEXT));
    }

    /** Tab Gói tài nguyên chỉ hiển thị chú thích. Mod không đọc, không đổi và không tải lại gói tài nguyên. */
    private void drawPacks(DrawContext ctx) {
        int base = contentTop() - scroll;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("pack.note1")),
                px + pw / 2, base + 4, IosStyle.fadeColor(IosStyle.TEXT_DIM));
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("pack.note2")),
                px + pw / 2, base + 16, IosStyle.fadeColor(IosStyle.TEXT_DIM));
    }

    private String trim(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        String out = s;
        while (out.length() > 1 && textRenderer.getWidth(out + "..") > maxPx) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
    }

    // ---------- tương tác ----------

    private boolean inContent(double my) {
        return my >= contentTop() && my < contentBottom();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (IosStyle.isClosing()) return true;

        // chuột phải vào thẻ: mở bảng cài đặt
        if (button == 1 && tab != Feature.Group.PACK && inContent(my)) {
            List<Feature> items = Feature.inGroup(tab);
            for (int i = 0; i < items.size(); i++) {
                if (inside(mx, my, cardX(i % 2), cardY(i / 2), cardW(), CARD_H)) {
                    openSettings(items.get(i));
                    return true;
                }
            }
        }
        if (button != 0) return super.mouseClicked(mx, my, button);

        for (int i = 0; i < TABS.length; i++) {
            if (inside(mx, my, tabX(i), tabY(), tabSlot(), PILL_H)) {
                if (TABS[i] != tab) {
                    tab = TABS[i];
                    scroll = 0;
                    scrollAcc = 0;
                    ElaraConfig.setMenuState(i, 0);
                }
                return true;
            }
        }

        int slider = sliderAt(mx, my);
        if (slider >= 0) {
            draggingSlider = slider;
            applySlider(slider, mx);
            return true;
        }

        if (inContent(my)) {
            pressing = true;
            dragged = false;
            pressX = mx;
            pressY = my;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Thanh trượt đảo (0: chiều cao, 1: vị trí) nằm dưới vạch chạm, hoặc -1 */
    private int sliderAt(double mx, double my) {
        if (tab != Feature.Group.MISC) return -1;
        int n = Feature.inGroup(tab).size();
        for (int i = 0; i < 2; i++) {
            int y = extraY(n, 3 + i);
            if (inside(mx, my, px + PAD - 4, y + 8, pw - PAD * 2 + 8, 14)) return i;
        }
        return -1;
    }

    /** Thả chuột mà không kéo đi xa thì coi là một lần chạm */
    private void tap(double mx, double my) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (tab == Feature.Group.PACK) return;
        List<Feature> items = Feature.inGroup(tab);
        int n = items.size();
        for (int i = 0; i < n; i++) {
            int x = cardX(i % 2);
            int y = cardY(i / 2);
            if (!inside(mx, my, x, y, cardW(), CARD_H)) continue;
            if (inside(mx, my, sliderX(x) - 4, y, SL_W + 8, CARD_H)) {
                openSettings(items.get(i));
            } else {
                toggle(items.get(i));
            }
            return;
        }
        if (tab == Feature.Group.FIX_LAG && inside(mx, my, px + PAD, extraY(n, 0), pw - PAD * 2, 20)) {
            long freed = MemoryTools.clean();
            gcMessage = freed < 0
                    ? Lang.get("ui.gc_wait") + MemoryTools.cooldownLeftSeconds() + "s"
                    : Lang.get("ui.gc_done") + freed + " MB";
        } else if (tab == Feature.Group.MISC) {
            tapMisc(client, n, mx, my);
        } else if (tab == Feature.Group.BUILDER
                && inside(mx, my, px + PAD, extraY(n, 0), pw - PAD * 2, 20)) {
            client.setScreen(new BuilderScreen(this));
        }
    }

    private void tapMisc(MinecraftClient client, int n, double mx, double my) {
        int full = pw - PAD * 2;
        if (inside(mx, my, px + PAD, extraY(n, 1), full, 20)) {
            Lang.toggle();
            ElaraConfig.save();
            return;
        }
        int sy = extraY(n, 2) + 8;
        if (inside(mx, my, px + PAD, sy, full, 20)) {
            int slot = full / SWATCHES.length;
            int idx = (int) ((mx - (px + PAD)) / slot);
            if (idx >= 0 && idx < SWATCHES.length) {
                ElaraConfig.setThemeAccent(SWATCHES[idx]);
                ElaraConfig.save();
            }
            return;
        }
        if (inside(mx, my, px + PAD, extraY(n, 5), full, 20)) {
            client.setScreen(new IslandEditorScreen(this));
        } else if (inside(mx, my, px + PAD, extraY(n, 6), full, 20)) {
            client.setScreen(new HudEditorScreen(this));
        } else if (inside(mx, my, px + PAD, extraY(n, 7), full, 20)) {
            client.setScreen(new MusicScreen(this));
        } else if (inside(mx, my, px + PAD, extraY(n, 8), full, 20)) {
            client.setScreen(new SkinScreen(this));
        }
    }

    /** Chỉ đổi cờ của Elara và ghi cấu hình Elara. Không tải lại thế giới hay tài nguyên. */
    private void toggle(Feature f) {
        ElaraConfig.toggle(f);
        ElaraConfig.save();
    }

    private void openSettings(Feature f) {
        MinecraftClient.getInstance().setScreen(new FeatureSettingsScreen(this, f));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        scroll = clampScroll(scroll - (int) Math.round(verticalAmount * 14));
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSlider >= 0) {
            applySlider(draggingSlider, mx);
            return true;
        }
        if (pressing && button == 0) {
            if (Math.abs(my - pressY) > DRAG_SLOP) dragged = true;
            if (dragged) {
                // gom phần lẻ của chuột để vuốt chậm vẫn mượt
                scrollAcc += dy;
                int step = (int) scrollAcc;
                scrollAcc -= step;
                scroll = clampScroll(scroll - step);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingSlider >= 0) {
            draggingSlider = -1;
            ElaraConfig.save();
            return true;
        }
        if (pressing && button == 0) {
            pressing = false;
            if (!dragged) tap(pressX, pressY);
            dragged = false;
            scrollAcc = 0;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void applySlider(int i, double mx) {
        float frac = (float) Math.max(0, Math.min(1, (mx - (px + PAD)) / (double) (pw - PAD * 2)));
        if (i == 0) {
            ElaraConfig.setIslandHeight(20 + Math.round(frac * 20));
        } else {
            ElaraConfig.setIslandOffsetY(Math.round(frac * 40));
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void close() {
        if (IosStyle.isClosing()) return;
        ElaraConfig.setMenuState(menuIndex(), scroll);
        ElaraConfig.save();
        IosStyle.beginClose();
    }
}
