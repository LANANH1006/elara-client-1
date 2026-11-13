package com.elara.client.ui;

import com.elara.client.ElaraClient;
import com.elara.client.core.AuthorGuard;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.core.LagFix;
import com.elara.client.core.MemoryTools;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Menu kính mờ với 6 tab */
public class ElaraMenuScreen extends Screen {
    /** Phase đã có code thật. Tab có phase lớn hơn số này sẽ hiện dòng "Hoàn thiện ở Phase". */
    public static final int BUILT_PHASE = 2;

    private static final Feature.Group[] TABS = Feature.Group.values();
    private static final int PAD = 10;
    private static final int TAB_H = 18;
    private static final int ROW_H = 24;

    private final Screen parent;
    private Feature.Group tab = Feature.Group.FIX_LAG;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int draggingSlider = -1;
    private String gcMessage = null;

    public ElaraMenuScreen(Screen parent) {
        super(Text.literal("Elara Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 340);
        ph = Math.min(height - 16, 280);
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
    private int trackX() { return px + PAD; }
    private int trackW() { return pw - PAD * 2; }

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

        List<Feature> items = Feature.inGroup(tab);
        for (int i = 0; i < items.size(); i++) {
            Feature f = items.get(i);
            int ry = rowY(i);
            ctx.drawText(textRenderer, Text.literal(f.label()), px + PAD, ry + 4, IosStyle.TEXT, false);
            IosStyle.toggleSwitch(ctx, toggleX(), ry + 4, ElaraConfig.isOn(f), f);
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
        }

        if (tab.phase > BUILT_PHASE) {
            ctx.drawText(textRenderer, Text.literal(Lang.get("ui.pending") + tab.phase),
                    px + PAD, py + ph - 28, IosStyle.TEXT_DIM, false);
        }

        if (!AuthorGuard.isValid()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("ui.tampered")),
                    px + pw / 2, py + ph - 40, 0xFFFF5555);
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Elara Client · " + ElaraClient.AUTHOR),
                px + pw / 2, py + ph - 14, IosStyle.TEXT_DIM);
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

        List<Feature> items = Feature.inGroup(tab);
        for (int i = 0; i < items.size(); i++) {
            if (inside(mx, my, toggleX() - 6, rowY(i), 42, ROW_H)) {
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
        }
        return super.mouseClicked(mx, my, button);
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
