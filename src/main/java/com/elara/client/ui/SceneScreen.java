package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Bảng chọn nền màn hình chính: ảnh tĩnh hoặc cảnh 3D, cùng giờ, loại cây và camera.
 * Mỗi lần bấm một dòng thì đổi sang lựa chọn kế tiếp và lưu ngay.
 */
public class SceneScreen extends Screen {
    private static final int ROW_H = 26;
    private static final int PAD = 12;
    private static final int HEAD_H = 32;
    private static final int FOOT_H = 26;

    /** Khóa lưu, số lựa chọn, tiêu đề dòng */
    private static final String[] KEYS = {"mode", "time", "tree", "cam"};
    private static final int[] COUNTS = {2, 4, 3, 3};
    private static final String[] TITLES = {"scene.mode", "scene.time", "scene.tree", "scene.cam"};

    private final Screen parent;
    private int px;
    private int py;
    private int pw;
    private int ph;

    public SceneScreen(Screen parent) {
        super(Text.literal("Scene"));
        this.parent = parent;
        IosStyle.beginOpen();
    }

    @Override
    protected void init() {
        pw = Math.min(width - 20, 280);
        ph = HEAD_H + ROW_H * KEYS.length + FOOT_H + 10;
        px = (width - pw) / 2;
        py = Math.max(8, (height - ph) / 2);
    }

    private int valueOf(int row) {
        return switch (row) {
            case 0 -> ElaraConfig.titleMode();
            case 1 -> ElaraConfig.sceneTime();
            case 2 -> ElaraConfig.sceneTree();
            default -> ElaraConfig.sceneCam();
        };
    }

    /** Tên của lựa chọn đang chọn trong một dòng */
    private String valueName(int row) {
        int v = valueOf(row);
        return switch (row) {
            case 0 -> Lang.get(v == 0 ? "scene.mode.static" : "scene.mode.3d");
            case 1 -> Lang.get(new String[] {"scene.time.dawn", "scene.time.noon", "scene.time.dusk", "scene.time.night"}[v]);
            case 2 -> Lang.get(new String[] {"scene.tree.cherry", "scene.tree.oak", "scene.tree.pine"}[v]);
            default -> Lang.get(new String[] {"scene.cam.orbit", "scene.cam.drift", "scene.cam.still"}[v]);
        };
    }

    private int rowY(int row) { return py + HEAD_H + row * ROW_H; }
    private int closeX() { return px + pw - PAD - 18; }
    private int footY() { return py + ph - FOOT_H; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        float open = IosStyle.openFactor();
        float s = 0.92f + 0.08f * open;
        float cx = px + pw / 2f;
        float cy = py + ph / 2f;
        IosStyle.setFade(open);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0f);
        ctx.getMatrices().scale(s, s, 1f);
        ctx.getMatrices().translate(-cx, -cy, 0f);
        try {
            drawModal(ctx);
        } finally {
            ctx.getMatrices().pop();
            IosStyle.setFade(1f);
        }
    }

    private void drawModal(DrawContext ctx) {
        IosStyle.glassPanel(ctx, px, py, pw, ph, 20);
        ctx.drawText(textRenderer, Text.literal(Lang.get("title.scene")), px + PAD, py + 12,
                IosStyle.fadeColor(IosStyle.TEXT), false);
        IosStyle.rounded(ctx, closeX(), py + 8, 18, 18, 9, IosStyle.fadeColor(IosStyle.TILE));
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("X"),
                closeX() + 9, py + 13, IosStyle.fadeColor(IosStyle.TEXT));

        for (int i = 0; i < KEYS.length; i++) {
            int y = rowY(i);
            IosStyle.rounded(ctx, px + PAD, y, pw - PAD * 2, ROW_H - 4, 10, IosStyle.fadeColor(IosStyle.TILE));
            ctx.drawText(textRenderer, Text.literal(Lang.get(TITLES[i])), px + PAD + 10, y + 6,
                    IosStyle.fadeColor(IosStyle.TEXT), false);
            String value = valueName(i) + "  >";
            ctx.drawText(textRenderer, Text.literal(value),
                    px + pw - PAD - 10 - textRenderer.getWidth(value), y + 6,
                    IosStyle.fadeColor(IosStyle.accent()), false);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("scene.hint")),
                px + pw / 2, footY() + 6, IosStyle.fadeColor(IosStyle.TEXT_DIM));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (inside(mx, my, closeX(), py + 8, 18, 18)) {
            close();
            return true;
        }
        for (int i = 0; i < KEYS.length; i++) {
            if (inside(mx, my, px + PAD, rowY(i), pw - PAD * 2, ROW_H - 4)) {
                int next = (valueOf(i) + 1) % COUNTS[i];
                ElaraConfig.setScene(KEYS[i], next);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
