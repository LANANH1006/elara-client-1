package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.core.LagFix;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Bảng cài đặt riêng của một tính năng, mở bằng chuột phải ở menu Elara. */
public class FeatureSettingsScreen extends Screen {
    private static final int PAD = 12;
    private static final int ROW_H = 26;
    private static final int TITLE_H = 30;
    private static final int SWATCH = 18;
    private static final int COLOR_X = 56;

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
        return hasPosition(f) || hasColor(f) || !extraControls(f).isEmpty();
    }

    /** Tính năng có vị trí trên màn hình (kéo được bằng thanh X, Y) */
    private static boolean hasPosition(Feature f) {
        return f == Feature.KEYSTROKES || f == Feature.SYSINFO || f == Feature.PIXEL_CROSSHAIR;
    }

    /** Tính năng có màu để chọn bằng bảng màu */
    private static boolean hasColor(Feature f) {
        return f == Feature.KEYSTROKES || f == Feature.SYSINFO || f == Feature.PIXEL_CROSSHAIR
                || f == Feature.JUMP_RING || f == Feature.VIRTUAL_WINGS
                || f == Feature.HEAD_ACCESSORY || f == Feature.FOOTSTEP_AURA || f == Feature.SWORD_TRAIL
                || f == Feature.PVP_REACH || f == Feature.HIT_EFFECT || f == Feature.RAIN_3D;
    }

    /** Danh sách chọn chế độ hiển thị dùng chung cho cánh và vòng nhảy */
    private static Ctl visibilityCtl() {
        String[] modes = {Lang.get("vis.self"), Lang.get("vis.nearby"), Lang.get("vis.third")};
        return new Ctl(Lang.get("set.vis"), "", 0, modes.length - 1,
                l -> l.get("vis", 0), (l, v) -> l.put("vis", v), modes);
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
        switch (f) {
            case PIXEL_CROSSHAIR -> {
                String[] shapes = {
                        Lang.get("shape.plus"), Lang.get("shape.dot"), Lang.get("shape.x"), Lang.get("shape.circle")
                };
                list.add(new Ctl(Lang.get("set.shape"), "", 0, shapes.length - 1,
                        l -> l.get("shape", 0), (l, v) -> l.put("shape", v), shapes));
                list.add(slider(Lang.get("set.length"), "", 2, 20, "length", 6));
                list.add(slider(Lang.get("set.thickness"), "", 1, 6, "thickness", 2));
                list.add(slider(Lang.get("set.gap"), "", 0, 10, "gap", 3));
            }
            case LAG_RENDER -> list.add(slider(Lang.get("set.view_cap"), "", 2, 16, "viewCap", 12));
            case LAG_GPU_SAVER -> {
                list.add(slider(Lang.get("set.view_cap"), "", 2, 16, "viewCap", 8));
                list.add(slider(Lang.get("set.fps_cap"), "", 15, 120, "fpsCap", 60));
            }
            case LAG_ENTITIES -> list.add(slider(Lang.get("set.entity_pct"), "%", 50, 100, "pct", 50));
            case LAG_BIOME -> list.add(slider(Lang.get("set.biome_radius"), "", 0, 7, "radius", 0));
            case LAG_MIPMAP -> list.add(slider(Lang.get("set.mipmap"), "", 0, 4, "levels", 0));
            case LAG_FOCUS_FPS -> list.add(slider(Lang.get("set.focus_fps"), "", 10, 60, "fps", 10));
            case JUMP_RING -> {
                String[] styles = {Lang.get("ring.wave"), Lang.get("ring.double"), Lang.get("ring.magic")};
                list.add(new Ctl(Lang.get("set.ring_style"), "", 0, styles.length - 1,
                        l -> l.get("style", 0), (l, v) -> l.put("style", v), styles));
                list.add(slider(Lang.get("set.speed"), "", 1, 10, "speed", 5));
                list.add(visibilityCtl());
            }
            case VIRTUAL_WINGS -> {
                String[] styles = {
                        Lang.get("wing.angel"), Lang.get("wing.dragon"),
                        Lang.get("wing.butterfly"), Lang.get("wing.cyber")
                };
                list.add(new Ctl(Lang.get("set.wing_style"), "", 0, styles.length - 1,
                        l -> l.get("style", 0), (l, v) -> l.put("style", v), styles));
                list.add(new Ctl(Lang.get("set.size"), "%", 50, 200, l -> l.scalePct, (l, v) -> l.scalePct = v, null));
                list.add(slider(Lang.get("set.flap"), "", 1, 10, "speed", 5));
                list.add(visibilityCtl());
            }
            case HEAD_ACCESSORY -> {
                list.add(new Ctl(Lang.get("set.size"), "%", 50, 200, l -> l.scalePct, (l, v) -> l.scalePct = v, null));
                list.add(visibilityCtl());
            }
            case FOOTSTEP_AURA -> {
                String[] styles = {Lang.get("foot.ring"), Lang.get("foot.fire")};
                list.add(new Ctl(Lang.get("set.foot_style"), "", 0, styles.length - 1,
                        l -> l.get("style", 0), (l, v) -> l.put("style", v), styles));
                list.add(visibilityCtl());
            }
            case SWORD_TRAIL -> list.add(visibilityCtl());
            case RAIN_3D -> {
                list.add(slider(Lang.get("set.density"), "", 20, 300, "density", 120));
                list.add(slider(Lang.get("set.speed"), "", 1, 10, "speed", 5));
            }
            case HIT_EFFECT -> {
                String[] styles = {
                        Lang.get("hit.rays"), Lang.get("hit.stars"), Lang.get("hit.hearts"), Lang.get("hit.sparks")
                };
                list.add(new Ctl(Lang.get("set.hit_style"), "", 0, styles.length - 1,
                        l -> l.get("style", 0), (l, v) -> l.put("style", v), styles));
            }
            case KNIFE_ANIM -> {
                list.add(slider(Lang.get("set.hand_x"), "", -50, 50, "hx", 0));
                list.add(slider(Lang.get("set.hand_y"), "", -50, 50, "hy", 0));
                list.add(slider(Lang.get("set.hand_z"), "", -50, 50, "hz", 0));
                list.add(slider(Lang.get("set.tilt"), "°", -90, 90, "tilt", 0));
                list.add(new Ctl(Lang.get("set.size"), "%", 50, 200, l -> l.scalePct, (l, v) -> l.scalePct = v, null));
            }
            default -> { }
        }
        return list;
    }

    /** Thanh trượt lưu vào Layout.extra với khóa và giá trị mặc định */
    private static Ctl slider(String label, String unit, int min, int max, String key, int def) {
        return new Ctl(label, unit, min, max, l -> l.get(key, def), (l, v) -> l.put(key, v), null);
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 300);
        ph = TITLE_H + ROW_H * (rows.size() + (hasColor(feature) ? 1 : 0)) + 16;
        px = (width - pw) / 2;
        py = Math.max(8, (height - ph) / 2);
    }

    private int rowY(int row) { return py + TITLE_H + row * ROW_H; }
    private int trackX() { return px + PAD; }
    private int trackW() { return pw - PAD * 2; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        IosStyle.glassPanel(ctx, px, py, pw, ph, 14);
        ElaraConfig.Layout l = ElaraConfig.layout(feature);
        ctx.drawText(textRenderer, Text.literal(feature.label()), px + PAD, py + 10, IosStyle.TEXT, false);

        for (int i = 0; i < rows.size(); i++) {
            drawRow(ctx, i, rows.get(i), l);
        }
        if (hasColor(feature)) drawColorRow(ctx);
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

    /** Dòng màu: ô xem trước màu hiện tại và nút mở bảng màu */
    private void drawColorRow(DrawContext ctx) {
        int ry = rowY(rows.size());
        ctx.drawText(textRenderer, Text.literal(Lang.get("set.color")), px + PAD, ry + 6, IosStyle.TEXT, false);
        IosStyle.rounded(ctx, px + COLOR_X, ry + 4, SWATCH, SWATCH, 4, ElaraConfig.color(feature));
        ctx.drawText(textRenderer, Text.literal(Lang.get("set.open_picker")),
                px + COLOR_X + SWATCH + 8, ry + 6, IosStyle.ACCENT, false);
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

        if (hasColor(feature)) {
            int ry = rowY(rows.size());
            if (inside(mx, my, px + PAD - 4, ry, pw - PAD * 2 + 8, ROW_H - 4)) {
                MinecraftClient.getInstance().setScreen(new ColorPickerScreen(this, feature));
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
        MinecraftClient client = MinecraftClient.getInstance();
        LagFix.sync(client);
        client.setScreen(parent);
    }
}
