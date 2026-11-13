package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Bảng cài đặt riêng của một tính năng, mở bằng chuột phải ở menu Elara. */
public class FeatureSettingsScreen extends Screen {
    private static final int[] COLORS = {
            0xFFFFFFFF, 0xFF30D158, 0xFF0A84FF, 0xFFFF9F0A, 0xFFFF453A, 0xFFBF5AF2
    };
    private static final int PAD = 12;
    private static final int ROW_H = 26;
    private static final int TITLE_H = 30;
    private static final int SWATCH = 18;
    private static final int SWATCH_START = 44;

    /** Đọc một giá trị trong Layout */
    public interface Getter {
        int get(ElaraConfig.Layout l);
    }

    /** Ghi một giá trị trong Layout */
    public interface Setter {
        void set(ElaraConfig.Layout l, int v);
    }

    /**
     * Một dòng cài đặt. Nếu {@code choices} khác null thì là danh sách chọn (bấm để đổi),
     * ngược lại là thanh trượt từ {@code min} đến {@code max}.
     */
    public record Ctl(String label, String unit, int min, int max, Getter get, Setter set, String[] choices) {}

    private final Screen parent;
    private final Feature feature;
    private final List<Ctl> rows;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int dragging = -1;

    public FeatureSettingsScreen(Screen parent, Feature feature) {
        super(Text.literal(feature.name()));
        this.parent = parent;
        this.feature = feature;
        this.rows = rowsFor(feature);
    }

    /** Có bảng cài đặt cho tính năng này không */
    public static boolean supports(Feature f) {
        return hasPosition(f) || !extraControls(f).isEmpty();
    }

    /** Tính năng có vị trí trên màn hình (kéo được bằng thanh X, Y) */
    private static boolean hasPosition(Feature f) {
        return f == Feature.KEYSTROKES || f == Feature.SYSINFO || f == Feature.PIXEL_CROSSHAIR;
    }

    private static List<Ctl> rowsFor(Feature f) {
        List<Ctl> list = new ArrayList<>();
        if (hasPosition(f)) {
            list.add(new Ctl(Lang.get("set.x"), "%", 0, 100, l -> l.xPct, (l, v) -> l.xPct = v, null));
            list.add(new Ctl(Lang.get("set.y"), "%", 0, 100, l -> l.yPct, (l, v) -> l.yPct = v, null));
            list.add(new Ctl(Lang.get("set.scale"), "%", 50, 200, l -> l.scalePct, (l, v) -> l.scalePct = v, null));
        }
        list.addAll(extraControls(f));
        return list;
    }

    /** Các dòng riêng của từng tính năng, lưu trong Layout.extra */
    private static List<Ctl> extraControls(Feature f) {
        List<Ctl> list = new ArrayList<>();
        if (f == Feature.PIXEL_CROSSHAIR) {
            String[] shapes = {
                    Lang.get("shape.plus"), Lang.get("shape.dot"), Lang.get("shape.x"), Lang.get("shape.circle")
            };
            list.add(new Ctl(Lang.get("set.shape"), "", 0, shapes.length - 1,
                    l -> l.get("shape", 0), (l, v) -> l.put("shape", v), shapes));
            list.add(new Ctl(Lang.get("set.length"), "", 2, 20,
                    l -> l.get("length", 6), (l, v) -> l.put("length", v), null));
            list.add(new Ctl(Lang.get("set.thickness"), "", 1, 6,
                    l -> l.get("thickness", 2), (l, v) -> l.put("thickness", v), null));
            list.add(new Ctl(Lang.get("set.gap"), "", 0, 10,
                    l -> l.get("gap", 3), (l, v) -> l.put("gap", v), null));
        }
        return list;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 300);
        ph = TITLE_H + ROW_H * (rows.size() + 1) + 16;
        px = (width - pw) / 2;
        py = Math.max(8, (height - ph) / 2);
    }

    private int rowY(int row) { return py + TITLE_H + row * ROW_H; }
    private int trackX() { return px + PAD; }
    private int trackW() { return pw - PAD * 2; }

    private int swatchX(int i) {
        int span = pw - PAD * 2 - SWATCH_START - SWATCH;
        return px + PAD + SWATCH_START + i * span / (COLORS.length - 1);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        IosStyle.glassPanel(ctx, px, py, pw, ph, 14);
        ElaraConfig.Layout l = ElaraConfig.layout(feature);
        ctx.drawText(textRenderer, Text.literal(feature.label()), px + PAD, py + 10, IosStyle.TEXT, false);

        for (int i = 0; i < rows.size(); i++) {
            drawRow(ctx, i, rows.get(i), l);
        }
        drawColorRow(ctx, l);
    }

    private void drawRow(DrawContext ctx, int row, Ctl c, ElaraConfig.Layout l) {
        int ry = rowY(row);
        int v = c.get().get(l);
        if (c.choices() != null) {
            int idx = Math.max(0, Math.min(c.choices().length - 1, v));
            ctx.drawText(textRenderer, Text.literal(c.label() + ": " + c.choices()[idx] + "  >"),
                    px + PAD, ry + 6, IosStyle.TEXT, false);
            return;
        }
        ctx.drawText(textRenderer, Text.literal(c.label() + ": " + v + c.unit()),
                px + PAD, ry + 1, IosStyle.TEXT, false);
        int ty = ry + 14;
        float frac = fraction(v, c.min(), c.max());
        IosStyle.rounded(ctx, trackX(), ty, trackW(), 6, 3, IosStyle.GRAY);
        IosStyle.rounded(ctx, trackX(), ty, Math.max(6, Math.round(trackW() * frac)), 6, 3, IosStyle.ACCENT);
    }

    private void drawColorRow(DrawContext ctx, ElaraConfig.Layout l) {
        int ry = rowY(rows.size());
        int cy = ry + 4;
        ctx.drawText(textRenderer, Text.literal(Lang.get("set.color")), px + PAD, ry + 9, IosStyle.TEXT, false);
        for (int i = 0; i < COLORS.length; i++) {
            int sx = swatchX(i);
            if (COLORS[i] == l.color) {
                IosStyle.rounded(ctx, sx - 2, cy - 2, SWATCH + 4, SWATCH + 4, (SWATCH + 4) / 2, IosStyle.TEXT);
            }
            IosStyle.rounded(ctx, sx, cy, SWATCH, SWATCH, SWATCH / 2, COLORS[i]);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        ElaraConfig.Layout l = ElaraConfig.layout(feature);
        for (int i = 0; i < rows.size(); i++) {
            Ctl c = rows.get(i);
            int ry = rowY(i);
            if (c.choices() != null) {
                if (inside(mx, my, px + PAD - 4, ry, pw - PAD * 2 + 8, ROW_H - 4)) {
                    int next = (c.get().get(l) + 1) % c.choices().length;
                    c.set().set(l, next);
                    ElaraConfig.save();
                    return true;
                }
            } else if (inside(mx, my, trackX() - 4, ry + 10, trackW() + 8, 14)) {
                dragging = i;
                applySlider(i, mx);
                return true;
            }
        }

        int cy = rowY(rows.size()) + 4;
        for (int i = 0; i < COLORS.length; i++) {
            if (inside(mx, my, swatchX(i) - 2, cy - 2, SWATCH + 4, SWATCH + 4)) {
                l.color = COLORS[i];
                ElaraConfig.save();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging >= 0) {
            applySlider(dragging, mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging >= 0) {
            dragging = -1;
            ElaraConfig.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void applySlider(int i, double mx) {
        Ctl c = rows.get(i);
        float frac = (float) Math.max(0, Math.min(1, (mx - trackX()) / (double) trackW()));
        int v = c.min() + Math.round(frac * (c.max() - c.min()));
        c.set().set(ElaraConfig.layout(feature), v);
    }

    private static float fraction(int v, int min, int max) {
        return Math.max(0f, Math.min(1f, (v - min) / (float) (max - min)));
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
