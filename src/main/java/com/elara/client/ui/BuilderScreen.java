package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.fx.BuildGuide;
import com.elara.client.i18n.Lang;
import com.elara.client.schematic.BuilderState;
import com.elara.client.schematic.Schematic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Bảng Builder: chọn file .litematic, đặt gốc, bấm khối để đổi sang khối thay thế (icon vật phẩm 3D) */
public class BuilderScreen extends Screen {
    private static final int PAD = 10;
    private static final int PILL_H = 18;
    private static final int ROW_H = 22;
    private static final int MAX_W = 340;
    private static final int MAX_H = 300;

    private final Screen parent;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int listTop;
    private int listBottom;
    /** Vị trí cuộn đang hiển thị và vị trí đích, để cuộn mượt */
    private float scroll;
    private float target;
    private List<Schematic.Entry> rows = List.of();

    public BuilderScreen(Screen parent) {
        super(Text.literal("Builder"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(MAX_W, width - 16);
        ph = Math.min(MAX_H, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listTop = py + 118;
        listBottom = py + ph - 28;
        rows = BuilderState.entries();
        target = clamp(target);
        scroll = target;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        IosStyle.glassPanel(ctx, px, py, pw, ph, 16);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("builder.title")),
                px + pw / 2, py + 10, IosStyle.TEXT);

        int full = pw - PAD * 2;
        int half = (full - 6) / 2;

        String file = BuilderState.selectedName();
        pill(ctx, px + PAD, py + 30, full, PILL_H,
                Lang.get("builder.file") + ": " + (file.isEmpty() ? Lang.get("builder.no_files") : file));

        BlockPos o = BuilderState.origin();
        String originText = Lang.get("builder.origin") + ": "
                + (o == null ? Lang.get("builder.no_origin") : o.getX() + " " + o.getY() + " " + o.getZ());
        pill(ctx, px + PAD, py + 52, full, PILL_H, originText);

        pill(ctx, px + PAD, py + 74, half, PILL_H, Lang.get("builder.set_origin"));
        pill(ctx, px + PAD + half + 6, py + 74, half, PILL_H, Lang.get("builder.reload"));

        Schematic s = BuilderState.loaded();
        String info;
        if (s == null) {
            info = BuilderState.error().isEmpty() ? Lang.get("builder.no_files") : BuilderState.error();
        } else {
            info = Lang.get("builder.total") + ": " + s.total();
            if (ElaraConfig.isOn(Feature.BUILD_GUIDE)) {
                info += "   " + Lang.get("builder.missing") + " " + BuildGuide.missing()
                        + "   " + Lang.get("builder.wrong") + " " + BuildGuide.wrong();
            }
        }
        ctx.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(info, full)),
                px + PAD, py + 98, IosStyle.TEXT_DIM, false);

        // Cuộn mượt: tiến dần về vị trí đích
        scroll += (target - scroll) * 0.35f;
        if (Math.abs(target - scroll) < 0.5f) scroll = target;

        ctx.enableScissor(px, listTop, px + pw, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            int y = listTop + i * ROW_H - Math.round(scroll);
            if (y + ROW_H < listTop || y > listBottom) continue;
            drawRow(ctx, rows.get(i), px + PAD, y, mouseX, mouseY);
        }
        ctx.disableScissor();

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("builder.hint")),
                px + pw / 2, py + ph - 18, IosStyle.TEXT_DIM);
    }

    private void drawRow(DrawContext ctx, Schematic.Entry e, int x, int y, int mx, int my) {
        int w = pw - PAD * 2;
        boolean hover = inside(mx, my, x, y, w, ROW_H);
        IosStyle.rounded(ctx, x, y + 1, w, ROW_H - 2, 6, hover ? IosStyle.TILE : 0x1AFFFFFF);

        String swap = ElaraConfig.remap(e.id());
        ItemStack icon = stackOf(swap);
        if (!icon.isEmpty()) ctx.drawItem(icon, x + 4, y + 3);

        String label = prettify(e.id());
        if (!swap.equals(e.id())) label += "  ->  " + prettify(swap);
        String count = "x" + e.count();
        int countW = textRenderer.getWidth(count);
        String fit = textRenderer.trimToWidth(label, w - 30 - countW);
        ctx.drawText(textRenderer, Text.literal(fit), x + 24, y + 7, IosStyle.TEXT, false);
        ctx.drawText(textRenderer, Text.literal(count), x + w - countW - 6, y + 7, IosStyle.TEXT_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int full = pw - PAD * 2;
            int half = (full - 6) / 2;
            if (inside(mx, my, px + PAD, py + 30, full, PILL_H)) {
                BuilderState.cycleFile();
                rows = BuilderState.entries();
                target = 0;
                scroll = 0;
                return true;
            }
            if (inside(mx, my, px + PAD, py + 74, half, PILL_H)) {
                MinecraftClient c = MinecraftClient.getInstance();
                if (c.player != null) BuilderState.setOrigin(c.player.getBlockPos());
                return true;
            }
            if (inside(mx, my, px + PAD + half + 6, py + 74, half, PILL_H)) {
                BuilderState.rescan();
                rows = BuilderState.entries();
                target = clamp(target);
                return true;
            }
            if (inside(mx, my, px + PAD, listTop, full, listBottom - listTop)) {
                int idx = (int) Math.floor((my - listTop + scroll) / ROW_H);
                if (idx >= 0 && idx < rows.size()) {
                    String id = rows.get(idx).id();
                    ElaraConfig.setRemap(id, BuilderState.nextSwap(id));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        target = clamp(target - (float) verticalAmount * ROW_H);
        return true;
    }

    private float clamp(float v) {
        float max = Math.max(0, rows.size() * ROW_H - (listBottom - listTop));
        return Math.max(0, Math.min(max, v));
    }

    private void pill(DrawContext ctx, int x, int y, int w, int h, String text) {
        IosStyle.rounded(ctx, x, y, w, h, h / 2, IosStyle.TILE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(text, w - 12)),
                x + w / 2, y + (h - 8) / 2, IosStyle.TEXT);
    }

    private static ItemStack stackOf(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) return ItemStack.EMPTY;
        Item item = Registries.BLOCK.get(key).asItem();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String prettify(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
