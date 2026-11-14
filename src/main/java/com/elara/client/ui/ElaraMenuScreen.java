package com.elara.client.ui;

import com.elara.client.ElaraClient;
import com.elara.client.core.AuthorGuard;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.core.LagFix;
import com.elara.client.core.MemoryTools;
import com.elara.client.core.PackManager;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Menu kính mờ với 6 tab */
public class ElaraMenuScreen extends Screen {
    private static final Feature.Group[] TABS = Feature.Group.values();
    /**
     * Tính năng chưa có code thật. Công tắc bị khóa và ghi "Sắp có",
     * không cho bật/tắt để tránh nút rỗng. Xóa khỏi tập này khi làm xong.
     */
    private static final Set<Feature> UNBUILT = EnumSet.of(
            Feature.RAIN_3D,
            Feature.KNIFE_ANIM, Feature.PVP_REACH, Feature.HIT_EFFECT
    );
    private static final int PAD = 10;
    private static final int TAB_H = 18;
    private static final int ROW_H = 20;
    private static final int PACK_VISIBLE = 6;

    private final Screen parent;
    private Feature.Group tab = Feature.Group.FIX_LAG;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int draggingSlider = -1;
    private String gcMessage = null;
    private int packScroll = 0;

    public ElaraMenuScreen(Screen parent) {
        super(Text.literal("Elara Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 340);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    // ---------- bố cục ----------

    private int tabSlot() { return (pw - PAD * 2) / TABS.length; }
    private int tabW() { return tabSlot() - 2; }
    private int tabX(int i) { return px + PAD + i * tabSlot(); }
    private int tabY() { return py + 10; }

    private int rowY(int index) { return py + 40 + index * ROW_H; }
    private int toggleX() { return px + pw - PAD - 30; }
    private int downX() { return toggleX() - 6 - 44; }
    private int upX() { return downX() - 4 - 36; }
    private int trackX() { return px + PAD; }
    private int trackW() { return pw - PAD * 2; }

    private static boolean isBuilt(Feature f) {
        return !UNBUILT.contains(f);
    }

    // ---------- vẽ ----------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        IosStyle.glassPanel(ctx, px, py, pw, ph, 16);

        for (int i = 0; i < TABS.length; i++) {
            boolean active = TABS[i] == tab;
            if (active) {
                IosStyle.rounded(ctx, tabX(i), tabY(), tabW(), TAB_H, 9, IosStyle.TILE);
            }
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(TABS[i].label()),
                    tabX(i) + tabW() / 2, tabY() + 5,
                    active ? IosStyle.TEXT : IosStyle.TEXT_DIM);
        }

        if (tab == Feature.Group.PACK) {
            drawPacks(ctx);
        } else {
            drawFeatureRows(ctx);
        }

        if (!AuthorGuard.isValid()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("ui.tampered")),
                    px + pw / 2, py + ph - 40, 0xFFFF5555);
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Elara Client · " + ElaraClient.AUTHOR),
                px + pw / 2, py + ph - 12, IosStyle.TEXT_DIM);
    }

    private void drawFeatureRows(DrawContext ctx) {
        List<Feature> items = Feature.inGroup(tab);
        for (int i = 0; i < items.size(); i++) {
            Feature f = items.get(i);
            int ry = rowY(i);
            if (isBuilt(f)) {
                ctx.drawText(textRenderer, Text.literal(f.label()), px + PAD, ry + 4, IosStyle.TEXT, false);
                IosStyle.toggleSwitch(ctx, toggleX(), ry + 4, ElaraConfig.isOn(f), f);
            } else {
                ctx.drawText(textRenderer, Text.literal(f.label()), px + PAD, ry + 4, IosStyle.TEXT_DIM, false);
                ctx.drawText(textRenderer, Text.literal(Lang.get("ui.soon")), toggleX() + 2, ry + 4,
                        IosStyle.TEXT_DIM, false);
            }
        }

        if (tab == Feature.Group.FIX_LAG) {
            int n = items.size();
            String label = gcMessage != null ? gcMessage : Lang.get("ui.gc");
            drawButton(ctx, n, label);
            ctx.drawText(textRenderer, Text.literal(Lang.get("ui.gc_hint")),
                    px + PAD, rowY(n + 1) + 4, IosStyle.TEXT_DIM, false);
        }

        if (tab == Feature.Group.MISC) {
            int n = items.size();
            int islandH = ElaraConfig.islandHeight();
            int offset = ElaraConfig.islandOffsetY();
            drawSlider(ctx, n, Lang.get("ui.island_height") + ": " + islandH, islandH, 20, 40);
            drawSlider(ctx, n + 1, Lang.get("ui.island_offset") + ": " + offset, offset, 0, 40);

            String langName = Lang.current() == Lang.Language.VI ? "Tiếng Việt" : "English";
            drawButton(ctx, n + 2, Lang.get("ui.language") + ": " + langName);
            drawButton(ctx, n + 3, Lang.get("ui.island_editor"));
            drawButton(ctx, n + 4, Lang.get("music.open"));
        }

    }

    /** Tab Gói tài nguyên: danh sách gói thật từ thư mục resourcepacks */
    private void drawPacks(DrawContext ctx) {
        List<PackManager.Entry> entries = PackManager.list(MinecraftClient.getInstance());
        if (entries.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("pack.empty")),
                    px + pw / 2, rowY(0) + 4, IosStyle.TEXT_DIM);
        }
        packScroll = Math.max(0, Math.min(packScroll, entries.size() - PACK_VISIBLE));
        for (int i = 0; i < PACK_VISIBLE && packScroll + i < entries.size(); i++) {
            PackManager.Entry e = entries.get(packScroll + i);
            int ry = rowY(i);
            ctx.drawText(textRenderer, Text.literal(trim(e.title(), 140)), px + PAD, ry + 4,
                    e.enabled() ? IosStyle.TEXT : IosStyle.TEXT_DIM, false);
            if (e.enabled()) {
                drawMiniButton(ctx, upX(), ry + 3, 36, Lang.get("pack.up"));
                drawMiniButton(ctx, downX(), ry + 3, 44, Lang.get("pack.down"));
            }
            IosStyle.toggleSwitch(ctx, toggleX(), ry + 4, e.enabled(), "pack:" + e.id());
        }
        drawButton(ctx, PACK_VISIBLE, Lang.get("pack.reload"));
        ctx.drawText(textRenderer, Text.literal(Lang.get("pack.hint")),
                px + PAD, rowY(PACK_VISIBLE + 1) + 4, IosStyle.TEXT_DIM, false);
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

    private void drawMiniButton(DrawContext ctx, int x, int y, int w, String text) {
        IosStyle.rounded(ctx, x, y, w, 18, 9, IosStyle.TILE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(text), x + w / 2, y + 5, IosStyle.TEXT);
    }

    private void drawSlider(DrawContext ctx, int row, String label, int value, int min, int max) {
        int ry = rowY(row);
        ctx.drawText(textRenderer, Text.literal(label), px + PAD, ry + 1, IosStyle.TEXT, false);
        int ty = ry + 14;
        int tw = trackW();
        IosStyle.rounded(ctx, trackX(), ty, tw, 6, 3, IosStyle.GRAY);
        float frac = Math.max(0f, Math.min(1f, (value - min) / (float) (max - min)));
        IosStyle.rounded(ctx, trackX(), ty, Math.max(6, Math.round(tw * frac)), 6, 3, IosStyle.ACCENT);
    }

    private void drawButton(DrawContext ctx, int row, String text) {
        int ry = rowY(row);
        IosStyle.rounded(ctx, px + PAD, ry, pw - PAD * 2, 18, 9, IosStyle.TILE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(text),
                px + pw / 2, ry + 5, IosStyle.TEXT);
    }

    // ---------- tương tác ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Chuột phải vào một tính năng: mở bảng cài đặt riêng của nó
        if (button == 1) {
            List<Feature> rows = Feature.inGroup(tab);
            for (int i = 0; i < rows.size(); i++) {
                if (inside(mx, my, px + PAD, rowY(i), pw - PAD * 2, ROW_H)
                        && FeatureSettingsScreen.supports(rows.get(i))) {
                    MinecraftClient.getInstance().setScreen(new FeatureSettingsScreen(this, rows.get(i)));
                    return true;
                }
            }
        }
        if (button != 0) return super.mouseClicked(mx, my, button);

        for (int i = 0; i < TABS.length; i++) {
            if (inside(mx, my, tabX(i), tabY(), tabW(), TAB_H)) {
                tab = TABS[i];
                return true;
            }
        }

        if (tab == Feature.Group.PACK) {
            return clickPacks(mx, my) || super.mouseClicked(mx, my, button);
        }

        List<Feature> items = Feature.inGroup(tab);
        for (int i = 0; i < items.size(); i++) {
            if (isBuilt(items.get(i)) && inside(mx, my, toggleX() - 6, rowY(i), 42, ROW_H)) {
                ElaraConfig.toggle(items.get(i));
                ElaraConfig.save();
                LagFix.sync(MinecraftClient.getInstance());
                return true;
            }
        }

        if (tab == Feature.Group.FIX_LAG) {
            int n = items.size();
            if (inside(mx, my, px + PAD, rowY(n), pw - PAD * 2, 18)) {
                long freed = MemoryTools.clean();
                gcMessage = freed < 0
                        ? Lang.get("ui.gc_wait") + MemoryTools.cooldownLeftSeconds() + "s"
                        : Lang.get("ui.gc_done") + freed + " MB";
                return true;
            }
        }

        if (tab == Feature.Group.MISC) {
            int n = items.size();
            for (int i = 0; i < 2; i++) {
                if (inside(mx, my, trackX() - 4, rowY(n + i) + 10, trackW() + 8, 14)) {
                    draggingSlider = i;
                    applySlider(i, mx);
                    return true;
                }
            }
            if (inside(mx, my, px + PAD, rowY(n + 2), pw - PAD * 2, 18)) {
                Lang.toggle();
                ElaraConfig.save();
                return true;
            }
            if (inside(mx, my, px + PAD, rowY(n + 3), pw - PAD * 2, 18)) {
                MinecraftClient.getInstance().setScreen(new IslandEditorScreen(this));
                return true;
            }
            if (inside(mx, my, px + PAD, rowY(n + 4), pw - PAD * 2, 18)) {
                MinecraftClient.getInstance().setScreen(new MusicScreen(this));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Bấm trong tab Gói: bật/tắt, lên/xuống, hoặc tải lại */
    private boolean clickPacks(double mx, double my) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<PackManager.Entry> entries = PackManager.list(client);
        for (int i = 0; i < PACK_VISIBLE && packScroll + i < entries.size(); i++) {
            PackManager.Entry e = entries.get(packScroll + i);
            int ry = rowY(i);
            if (inside(mx, my, toggleX() - 6, ry, 42, ROW_H)) {
                PackManager.setEnabled(client, e.id(), !e.enabled());
                return true;
            }
            if (e.enabled() && inside(mx, my, upX(), ry + 3, 36, 18)) {
                PackManager.move(client, e.id(), -1);
                return true;
            }
            if (e.enabled() && inside(mx, my, downX(), ry + 3, 44, 18)) {
                PackManager.move(client, e.id(), 1);
                return true;
            }
        }
        if (inside(mx, my, px + PAD, rowY(PACK_VISIBLE), pw - PAD * 2, 18)) {
            PackManager.reload(client);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (tab == Feature.Group.PACK) {
            packScroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mx, my, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSlider >= 0) {
            applySlider(draggingSlider, mx);
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
        return super.mouseReleased(mx, my, button);
    }

    private void applySlider(int i, double mx) {
        float frac = (float) Math.max(0, Math.min(1, (mx - trackX()) / (double) trackW()));
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
        ElaraConfig.save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
