package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.i18n.Lang;
import com.elara.client.skin.SkinPreview;
import com.elara.client.skin.SkinStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Tab Skin: tải skin theo tên người chơi Minecraft, vẽ trên lưới 64x64 (chạm để vẽ, kéo để tô liên tục),
 * chọn tay rộng hoặc tay mảnh, ẩn hoặc hiện từng bộ phận (đầu, thân, hai tay, hai chân, lớp ngoài),
 * chép một bộ phận từ skin đã tải, và xem nhân vật 3D xoay được (kéo khung xem trước).
 */
public class SkinScreen extends Screen {
    private static final int SCALE = 2;
    private static final int CANVAS = SkinStore.SIZE * SCALE;
    private static final int[] PALETTE = {
            0xFF1E1E1E, 0xFFFFFFFF, 0xFFE0AC69, 0xFF8D5524, 0xFFC68642, 0xFFF1C27D,
            0xFF2E86DE, 0xFFE74C3C, 0xFF2ECC71, 0xFFF1C40F, 0xFF9B59B6, 0xFF34495E
    };
    private static final String[] TOOL_KEYS = {"skin.tool_brush", "skin.tool_erase", "skin.tool_fill", "skin.tool_pick"};
    private static final String[] PART_KEYS = {
            "skin.part_head", "skin.part_body", "skin.part_larm", "skin.part_rarm",
            "skin.part_lleg", "skin.part_rleg", "skin.part_outer"};

    private final Screen parent;
    private final TextBox name = new TextBox(16);
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int tool = 0;
    private int color = PALETTE[0];
    private int part = 0;
    private double yaw = 25;
    private boolean painting;
    private boolean draggingYaw;

    public SkinScreen(Screen parent) {
        super(Text.literal("Skin"));
        this.parent = parent;
        SkinStore.ensureLoaded();
        this.name.setText(ElaraConfig.skinName());
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 330);
        ph = Math.min(height - 10, 324);
        px = (width - pw) / 2;
        py = Math.max(5, (height - ph) / 2);
    }

    // ---------- toạ độ ----------

    private int cx() { return px + 12; }
    private int cy() { return py + 60; }
    private int prX() { return px + 12 + CANVAS + 10; }
    private int prW() { return pw - (12 + CANVAS + 10) - 12; }
    private int nameX() { return px + 12; }
    private int nameW() { return pw - 24 - 50; }
    private int fetchX() { return px + pw - 12 - 44; }
    private int paletteY() { return py + 194; }
    private int toolY() { return py + 212; }
    private int modelY() { return py + 232; }
    private int partY() { return py + 252; }
    private int actY() { return py + 270; }
    private int footY() { return py + 292; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SkinStore.refresh();
        renderBackground(ctx, mouseX, mouseY, delta);
        IosStyle.glassPanel(ctx, px, py, pw, ph, 14);
        ctx.drawText(textRenderer, Text.literal(Lang.get("skin.title")), px + 12, py + 9, IosStyle.TEXT, false);

        // Ô tên và nút tải
        boolean empty = name.text().isEmpty() && !name.focused();
        IosStyle.rounded(ctx, nameX(), py + 24, nameW(), 16, 5, 0x55000000);
        String shown = empty ? Lang.get("skin.name_hint") : name.text() + (name.focused() ? name.caret() : "");
        ctx.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(shown, nameW() - 8)),
                nameX() + 4, py + 28, empty ? IosStyle.TEXT_DIM : IosStyle.TEXT, false);
        button(ctx, fetchX(), py + 24, 44, 16, Lang.get("skin.fetch"), mouseX, mouseY, false);

        String msg = SkinStore.busy() ? Lang.get("skin.fetching") : SkinStore.message();
        ctx.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(msg, pw - 24)),
                px + 12, py + 44, IosStyle.TEXT_DIM, false);

        // Canvas 64x64 phóng to, vẽ từ chính texture đang dùng (bộ phận ẩn hiện trong suốt)
        IosStyle.rounded(ctx, cx() - 3, cy() - 3, CANVAS + 6, CANVAS + 6, 6, 0x66000000);
        ctx.drawTexture(RenderLayer::getGuiTextured, SkinStore.TEXTURE, cx(), cy(), 0f, 0f,
                CANVAS, CANVAS, SkinStore.SIZE, SkinStore.SIZE);

        // Xem trước 3D xoay được
        IosStyle.rounded(ctx, prX(), cy(), prW(), CANVAS, 6, 0x66000000);
        SkinPreview.draw(prX(), cy(), prW(), CANVAS, yaw, ElaraConfig.skinSlim());

        // Bảng màu: ô đang chọn có viền sáng
        int cell = (pw - 24) / PALETTE.length;
        for (int i = 0; i < PALETTE.length; i++) {
            int x = px + 12 + i * cell;
            ctx.fill(x, paletteY(), x + cell - 2, paletteY() + 14, PALETTE[i]);
            if (PALETTE[i] == color) {
                ctx.fill(x - 1, paletteY() - 1, x + cell - 1, paletteY(), 0xFFFFFFFF);
                ctx.fill(x - 1, paletteY() + 14, x + cell - 1, paletteY() + 15, 0xFFFFFFFF);
            }
        }

        // Công cụ
        int tw = (pw - 24 - 9) / 4;
        for (int i = 0; i < 4; i++) {
            button(ctx, px + 12 + i * (tw + 3), toolY(), tw, 16, Lang.get(TOOL_KEYS[i]), mouseX, mouseY, tool == i);
        }

        // Kiểu tay
        int mw = (pw - 27) / 2;
        button(ctx, px + 12, modelY(), mw, 16, Lang.get("skin.classic"), mouseX, mouseY, !ElaraConfig.skinSlim());
        button(ctx, px + 15 + mw, modelY(), mw, 16, Lang.get("skin.slim"), mouseX, mouseY, ElaraConfig.skinSlim());

        // Chọn bộ phận: chip đang chọn sáng, bộ phận đang ẩn có dấu gạch
        int chipW = (pw - 24 - 12) / PART_KEYS.length;
        for (int i = 0; i < PART_KEYS.length; i++) {
            String label = Lang.get(PART_KEYS[i]);
            if (SkinStore.hidden(SkinStore.parts()[i])) label = "×" + label;
            button(ctx, px + 12 + i * (chipW + 2), partY(), chipW, 14, label, mouseX, mouseY, part == i);
        }

        // Ẩn/hiện, chép từ skin tải, lưu, bật/tắt skin
        String current = SkinStore.parts()[part];
        button(ctx, px + 12, actY(), mw, 16,
                Lang.get(SkinStore.hidden(current) ? "skin.show" : "skin.hide"), mouseX, mouseY, false);
        button(ctx, px + 15 + mw, actY(), mw, 16, Lang.get("skin.copy"), mouseX, mouseY, false);
        button(ctx, px + 12, footY(), mw, 16, Lang.get("skin.save"), mouseX, mouseY, false);
        button(ctx, px + 15 + mw, footY(), mw, 16,
                Lang.get(ElaraConfig.skinOn() ? "skin.disable" : "skin.enable"), mouseX, mouseY, ElaraConfig.skinOn());

        ctx.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(Lang.get("skin.hint"), pw - 24)),
                px + 12, py + 312, IosStyle.TEXT_DIM, false);
    }

    private void button(DrawContext ctx, int x, int y, int w, int h, String label, int mx, int my, boolean active) {
        boolean hover = inside(mx, my, x, y, w, h);
        int bg = active ? IosStyle.accent() : (hover ? 0xFF4A4A4F : 0xFF3A3A3C);
        IosStyle.rounded(ctx, x, y, w, h, 5, bg);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(label, w - 4)),
                x + w / 2, y + (h - 8) / 2, IosStyle.TEXT);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean onCanvas(double mx, double my) {
        return inside(mx, my, cx(), cy(), CANVAS, CANVAS);
    }

    // ---------- chuột ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        if (inside(mx, my, nameX(), py + 24, nameW(), 16)) {
            name.setFocused(true);
            return true;
        }
        name.setFocused(false);
        if (inside(mx, my, fetchX(), py + 24, 44, 16)) {
            SkinStore.fetch(name.text());
            return true;
        }
        if (onCanvas(mx, my)) {
            painting = true;
            applyTool(canvasX(mx), canvasY(my));
            return true;
        }
        if (inside(mx, my, prX(), cy(), prW(), CANVAS)) {
            draggingYaw = true;
            return true;
        }

        int cell = (pw - 24) / PALETTE.length;
        if (inside(mx, my, px + 12, paletteY(), cell * PALETTE.length, 14)) {
            int idx = (int) ((mx - (px + 12)) / cell);
            if (idx >= 0 && idx < PALETTE.length) color = PALETTE[idx];
            return true;
        }

        int tw = (pw - 24 - 9) / 4;
        for (int i = 0; i < 4; i++) {
            if (inside(mx, my, px + 12 + i * (tw + 3), toolY(), tw, 16)) {
                tool = i;
                return true;
            }
        }

        int mw = (pw - 27) / 2;
        if (inside(mx, my, px + 12, modelY(), mw, 16)) {
            ElaraConfig.setSkinSlim(false);
            return true;
        }
        if (inside(mx, my, px + 15 + mw, modelY(), mw, 16)) {
            ElaraConfig.setSkinSlim(true);
            return true;
        }

        int chipW = (pw - 24 - 12) / PART_KEYS.length;
        for (int i = 0; i < PART_KEYS.length; i++) {
            if (inside(mx, my, px + 12 + i * (chipW + 2), partY(), chipW, 14)) {
                part = i;
                return true;
            }
        }

        String current = SkinStore.parts()[part];
        if (inside(mx, my, px + 12, actY(), mw, 16)) {
            SkinStore.toggleHidden(current);
            return true;
        }
        if (inside(mx, my, px + 15 + mw, actY(), mw, 16)) {
            SkinStore.copyPart(current);
            return true;
        }
        if (inside(mx, my, px + 12, footY(), mw, 16)) {
            SkinStore.save();
            return true;
        }
        if (inside(mx, my, px + 15 + mw, footY(), mw, 16)) {
            ElaraConfig.setSkinOn(!ElaraConfig.skinOn());
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (painting && onCanvas(mx, my) && (tool == 0 || tool == 1)) {
            applyTool(canvasX(mx), canvasY(my));
            return true;
        }
        if (draggingYaw) {
            yaw += dx * 1.2;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        painting = false;
        draggingYaw = false;
        return super.mouseReleased(mx, my, button);
    }

    private int canvasX(double mx) {
        return (int) ((mx - cx()) / SCALE);
    }

    private int canvasY(double my) {
        return (int) ((my - cy()) / SCALE);
    }

    /** Áp công cụ đang chọn lên một pixel. Đổ màu và lấy màu chỉ chạy khi chạm, không chạy khi kéo. */
    private void applyTool(int x, int y) {
        switch (tool) {
            case 0 -> SkinStore.paint(x, y, color);
            case 1 -> SkinStore.paint(x, y, 0);
            case 2 -> SkinStore.floodFill(x, y, color);
            default -> {
                int argb = SkinStore.pixel(x, y);
                if ((argb >>> 24) != 0) color = argb | 0xFF000000;
            }
        }
    }

    // ---------- bàn phím ----------

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (name.focused()) {
            name.append(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (name.focused()) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                name.backspace();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                SkinStore.fetch(name.text());
                name.setFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                name.appendAll(MinecraftClient.getInstance().keyboard.getClipboard());
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        SkinStore.saveIfEdited();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
