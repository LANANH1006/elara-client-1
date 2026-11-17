package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.fx.WorldFx;
import com.elara.client.i18n.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bảng cài đặt riêng của một tính năng: hộp kính ở giữa màn hình, có khung xem trước,
 * thanh trượt, danh sách chọn, bảng màu và nút Đặt lại mặc định.
 * Mở bằng nút sliders trên thẻ hoặc chuột phải.
 */
public class FeatureSettingsScreen extends Screen {
    private static final int PAD = 14;
    private static final int ROW_H = 30;
    private static final int HEAD_H = 34;
    private static final int PREVIEW_H = 56;
    private static final int FOOT_H = 30;
    private static final int DRAG_SLOP = 4;

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
    private int viewH;
    private int scroll = 0;
    private double scrollAcc = 0;
    private int dragging = -1;
    private boolean pressing = false;
    private boolean dragged = false;
    private double pressX;
    private double pressY;

    public FeatureSettingsScreen(Screen parent, Feature feature) {
        super(Text.literal(feature.name()));
        this.parent = parent;
        this.feature = feature;
        this.rows = rowsFor(feature);
        IosStyle.beginOpen();
    }

    /** Tính năng có vị trí trên màn hình (kéo được bằng thanh X, Y) */
    private static boolean hasPosition(Feature f) {
        return f == Feature.KEYSTROKES || f == Feature.SYSINFO || f == Feature.PIXEL_CROSSHAIR
                || f == Feature.TARGET_HUD;
    }

    /** Tính năng có màu để chọn bằng bảng màu */
    private static boolean hasColor(Feature f) {
        return f == Feature.KEYSTROKES || f == Feature.SYSINFO || f == Feature.PIXEL_CROSSHAIR
                || f == Feature.JUMP_RING || f == Feature.VIRTUAL_WINGS
                || f == Feature.HEAD_ACCESSORY || f == Feature.FOOTSTEP_AURA || f == Feature.SWORD_TRAIL
                || f == Feature.PVP_REACH || f == Feature.HIT_EFFECT || f == Feature.RAIN_3D
                || f == Feature.CAPE || f == Feature.BODY_GLOW || f == Feature.RIBBON_TRAIL
                || f == Feature.WEAPON_3D || f == Feature.TARGET_HUD || f == Feature.SHIELD_BREAKER
                || f == Feature.HIT_RIPPLE || f == Feature.AFTERGLOW || f == Feature.SHOCKWAVE
                || f == Feature.LOW_FIRE || f == Feature.AMBIENCE || f == Feature.RAIN_RIPPLE
                || f == Feature.CHROMA_WORLD || f == Feature.MIST_GLASS;
    }

    /** Ba mức: thấp, vừa, cao */
    private static String[] levels() {
        return new String[] {Lang.get("level.low"), Lang.get("level.med"), Lang.get("level.high")};
    }

    /** Tắt hoặc bật */
    private static String[] onOff() {
        return new String[] {Lang.get("level.off"), Lang.get("level.on")};
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
            // Đổi bằng thanh trượt thì bỏ vị trí pixel để phần trăm có hiệu lực
            list.add(new Ctl(Lang.get("set.x"), "%", 0, 100, l -> l.xPct, (l, v) -> { l.xPct = v; l.xPx = -1; }, null));
            list.add(new Ctl(Lang.get("set.y"), "%", 0, 100, l -> l.yPct, (l, v) -> { l.yPct = v; l.yPx = -1; }, null));
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
            case LAG_ENTITIES -> {
                list.add(slider(Lang.get("set.entity_pct"), "%", 50, 100, "pct", 50));
                list.add(slider(Lang.get("set.entity_dist"), "", 8, 48, "dist", 48));
            }
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
                        Lang.get("wing.butterfly"), Lang.get("wing.cyber"), Lang.get("wing.galaxy")
                };
                list.add(new Ctl(Lang.get("set.wing_style"), "", 0, styles.length - 1,
                        l -> l.get("style", 0), (l, v) -> l.put("style", v), styles));
                list.add(new Ctl(Lang.get("set.size"), "%", 50, 200, l -> l.scalePct, (l, v) -> l.scalePct = v, null));
                list.add(slider(Lang.get("set.flap"), "", 1, 10, "speed", 5));
                list.add(visibilityCtl());
            }
            case HEAD_ACCESSORY -> {
                String[] halos = {Lang.get("halo.ring"), Lang.get("halo.wave")};
                list.add(new Ctl(Lang.get("set.style"), "", 0, halos.length - 1,
                        l -> l.get("style", 0), (l, v) -> l.put("style", v), halos));
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
            case LAG_BLOCK_ENTITIES -> list.add(slider(Lang.get("set.block_dist"), "", 8, 48, "dist", 24));
            case CAPE -> {
                String[] capes = {Lang.get("cape.sakura"), Lang.get("cape.glass"), Lang.get("cape.neon")};
                list.add(new Ctl(Lang.get("set.style"), "", 0, capes.length - 1,
                        l -> l.get("style", 0), (l, v) -> l.put("style", v), capes));
                list.add(new Ctl(Lang.get("set.size"), "%", 50, 200, l -> l.scalePct, (l, v) -> l.scalePct = v, null));
                list.add(visibilityCtl());
            }
            case BODY_GLOW -> {
                list.add(slider(Lang.get("set.glow_thick"), "", 2, 40, "thick", 10));
                list.add(visibilityCtl());
            }
            case RIBBON_TRAIL -> {
                list.add(slider(Lang.get("set.trail_len"), "", 4, 24, "len", 16));
                list.add(new Ctl(Lang.get("set.size"), "%", 50, 200, l -> l.scalePct, (l, v) -> l.scalePct = v, null));
                list.add(visibilityCtl());
            }
            case WEAPON_3D -> {
                String[] weapons = {Lang.get("weapon.cs2"), Lang.get("weapon.katana")};
                list.add(new Ctl(Lang.get("set.style"), "", 0, weapons.length - 1,
                        l -> l.get("style", 0), (l, v) -> l.put("style", v), weapons));
                list.add(new Ctl(Lang.get("set.size"), "%", 50, 200, l -> l.scalePct, (l, v) -> l.scalePct = v, null));
            }
            case HIT_RIPPLE -> list.add(slider(Lang.get("set.wave_r"), "", 5, 30, "radius", 10));
            case AFTERGLOW -> list.add(slider(Lang.get("set.particles"), "", 20, 150, "count", 60));
            case SHOCKWAVE -> list.add(slider(Lang.get("set.shock_r"), "", 5, 60, "radius", 25));
            case LOW_FIRE -> list.add(slider(Lang.get("set.fire_drop"), "%", 0, 100, "drop", 45));
            case LAG_PARTICLES -> list.add(slider(Lang.get("set.particle_dist"), "", 8, 32, "dist", 16));
            case WORLD_SKY -> {
                String[] skies = new String[WorldFx.SKY_STYLES];
                for (int i = 0; i < skies.length; i++) skies[i] = Lang.get("sky." + i);
                list.add(new Ctl(Lang.get("set.sky_style"), "", 0, skies.length - 1,
                        l -> l.get("style", 0), (l, v) -> l.put("style", v), skies));
            }
            case AMBIENCE -> {
                String[] kinds = {
                        Lang.get("amb.motes"), Lang.get("amb.stardust"), Lang.get("amb.petals"), Lang.get("amb.snow")
                };
                list.add(new Ctl(Lang.get("set.amb_style"), "", 0, kinds.length - 1,
                        l -> l.get("style", 0), (l, v) -> l.put("style", v), kinds));
                list.add(new Ctl(Lang.get("set.density"), "", 0, 2,
                        l -> l.get("density", 1), (l, v) -> l.put("density", v), levels()));
            }
            case RAIN_RIPPLE -> {
                list.add(slider(Lang.get("set.ripple_count"), "", 10, WorldFx.RIPPLE_MAX, "count", WorldFx.RIPPLE_DEFAULT));
                list.add(new Ctl(Lang.get("set.wet_ground"), "", 0, 1,
                        l -> l.get("wet", 0), (l, v) -> l.put("wet", v), onOff()));
                list.add(new Ctl(Lang.get("set.rain_force"), "", 0, 1,
                        l -> l.get("force", 0), (l, v) -> l.put("force", v),
                        new String[] {Lang.get("rain.when_rain"), Lang.get("rain.always")}));
            }
            case SOFT_SHADOW -> list.add(slider(Lang.get("set.shadow_strength"), "%", 10, 100, "strength", 45));
            case CHROMA_WORLD -> list.add(slider(Lang.get("set.tint_strength"), "%", 0, 100, "strength", 35));
            case MIST_GLASS -> {
                String[] kinds = {Lang.get("mist.mist"), Lang.get("mist.glass"), Lang.get("mist.bokeh")};
                list.add(new Ctl(Lang.get("set.mist_style"), "", 0, kinds.length - 1,
                        l -> l.get("style", 0), (l, v) -> l.put("style", v), kinds));
                list.add(new Ctl(Lang.get("set.density"), "", 0, 2,
                        l -> l.get("density", 1), (l, v) -> l.put("density", v), levels()));
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

    // ---------- bố cục ----------

    @Override
    protected void init() {
        pw = Math.min(width - 20, 300);
        int fixed = HEAD_H + PREVIEW_H + 8 + FOOT_H + 8;
        int room = Math.max(60, height - 16 - fixed);
        viewH = Math.min(contentHeight(), room);
        ph = fixed + viewH;
        px = (width - pw) / 2;
        py = Math.max(8, (height - ph) / 2);
    }

    private int itemCount() {
        return rows.size() + (hasColor(feature) ? 1 : 0);
    }

    private int contentHeight() {
        return Math.max(1, itemCount()) * ROW_H;
    }

    private int contentTop() { return py + HEAD_H + PREVIEW_H + 8; }
    private int rowY(int i) { return contentTop() - scroll + i * ROW_H; }
    private int trackX() { return px + PAD; }
    private int trackW() { return pw - PAD * 2; }
    private int closeX() { return px + pw - PAD - 18; }
    private int resetY() { return py + ph - FOOT_H + 5; }
    private boolean inContent(double my) { return my >= contentTop() && my < contentTop() + viewH; }

    private int clampScroll(int v) {
        return Math.max(0, Math.min(v, Math.max(0, contentHeight() - viewH)));
    }

    // ---------- vẽ ----------

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
        ElaraConfig.Layout l = ElaraConfig.layout(feature);
        boolean on = ElaraConfig.isOn(feature);
        IosStyle.glassPanel(ctx, px, py, pw, ph, 20);

        // đầu: icon, tên, nút đóng
        IosStyle.rounded(ctx, px + PAD, py + 9, 18, 18, 6,
                IosStyle.fadeColor(on ? IosStyle.accent() : IosStyle.GRAY));
        String letter = feature.label().isEmpty() ? "?" : feature.label().substring(0, 1).toUpperCase(Locale.ROOT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(letter),
                px + PAD + 9, py + 14, IosStyle.fadeColor(IosStyle.TEXT));
        ctx.drawText(textRenderer, Text.literal(trim(feature.label(), closeX() - (px + PAD + 26) - 6)),
                px + PAD + 26, py + 14, IosStyle.fadeColor(IosStyle.TEXT), false);
        IosStyle.rounded(ctx, closeX(), py + 9, 18, 18, 9, IosStyle.fadeColor(IosStyle.TILE));
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("X"),
                closeX() + 9, py + 14, IosStyle.fadeColor(IosStyle.TEXT));

        // khung xem trước: màu hiện tại (kể cả Chroma) và quầng sáng
        int prY = py + HEAD_H;
        IosStyle.rounded(ctx, px + PAD, prY, pw - PAD * 2, PREVIEW_H - 6, 12, IosStyle.fadeColor(0x66000000));
        ctx.drawText(textRenderer, Text.literal(Lang.get("ui.preview")), px + PAD + 8, prY + 6,
                IosStyle.fadeColor(IosStyle.TEXT_DIM), false);
        int sample = ElaraConfig.color(feature);
        int sx = px + pw / 2 - 14;
        int sy = prY + 14;
        IosStyle.rounded(ctx, sx - 4, sy - 4, 36, 28, 10, IosStyle.fadeColor(IosStyle.withAlpha(sample, 0.25f)));
        IosStyle.rounded(ctx, sx, sy, 28, 20, 8, IosStyle.fadeColor(sample));

        // nội dung: cắt theo vùng xem, cuộn được
        ctx.enableScissor(px, contentTop(), px + pw, contentTop() + viewH);
        if (itemCount() == 0) {
            ctx.drawText(textRenderer, Text.literal(Lang.get("ui.no_options")), px + PAD, contentTop() + 8,
                    IosStyle.fadeColor(IosStyle.TEXT_DIM), false);
        }
        for (int i = 0; i < rows.size(); i++) {
            drawRow(ctx, i, rows.get(i), l);
        }
        if (hasColor(feature)) drawColorRow(ctx, rows.size());
        ctx.disableScissor();
        drawScrollbar(ctx);

        // chân: đặt lại mặc định
        IosStyle.rounded(ctx, px + PAD, resetY(), pw - PAD * 2, 20, 10, IosStyle.fadeColor(IosStyle.TILE));
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Lang.get("ui.reset_defaults")),
                px + pw / 2, resetY() + 6, IosStyle.fadeColor(IosStyle.TEXT));
    }

    private void drawScrollbar(DrawContext ctx) {
        int total = contentHeight();
        if (total <= viewH) return;
        int thumbH = Math.max(14, viewH * viewH / total);
        int thumbY = contentTop() + (viewH - thumbH) * scroll / (total - viewH);
        IosStyle.rounded(ctx, px + pw - 6, thumbY, 3, thumbH, 1,
                IosStyle.fadeColor(IosStyle.withAlpha(IosStyle.accent(), 0.8f)));
    }

    private void drawRow(DrawContext ctx, int row, Ctl c, ElaraConfig.Layout l) {
        int ry = rowY(row);
        int v = c.get().get(l);
        if (c.choices() != null) {
            int idx = Math.max(0, Math.min(c.choices().length - 1, v));
            ctx.drawText(textRenderer, Text.literal(trim(c.label() + ": " + c.choices()[idx] + "  >", trackW())),
                    px + PAD, ry + 9, IosStyle.fadeColor(IosStyle.TEXT), false);
            return;
        }
        ctx.drawText(textRenderer, Text.literal(trim(c.label() + ": " + v + c.unit(), trackW())),
                px + PAD, ry + 2, IosStyle.fadeColor(IosStyle.TEXT), false);
        float frac = fraction(v, c.min(), c.max());
        IosStyle.rounded(ctx, trackX(), ry + 16, trackW(), 6, 3, IosStyle.fadeColor(IosStyle.GRAY));
        IosStyle.rounded(ctx, trackX(), ry + 16, Math.max(6, Math.round(trackW() * frac)), 6, 3,
                IosStyle.fadeColor(IosStyle.accent()));
    }

    /** Dòng màu: nhãn, rồi ô xem màu hiện tại ở bên phải */
    private void drawColorRow(DrawContext ctx, int row) {
        int ry = rowY(row);
        ctx.drawText(textRenderer, Text.literal(Lang.get("set.color") + "  >  " + Lang.get("set.open_picker")),
                px + PAD, ry + 9, IosStyle.fadeColor(IosStyle.TEXT), false);
        IosStyle.rounded(ctx, px + pw - PAD - 18, ry + 6, 18, 18, 6,
                IosStyle.fadeColor(ElaraConfig.color(feature)));
    }

    // ---------- tương tác ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (inside(mx, my, closeX(), py + 9, 18, 18)) {
            close();
            return true;
        }
        if (inside(mx, my, px + PAD, resetY(), pw - PAD * 2, 20)) {
            ElaraConfig.resetLayout(feature);
            ElaraConfig.save();
            scroll = 0;
            return true;
        }
        if (!inContent(my)) return super.mouseClicked(mx, my, button);

        for (int i = 0; i < rows.size(); i++) {
            Ctl c = rows.get(i);
            if (c.choices() == null && inside(mx, my, px + PAD - 4, rowY(i) + 10, trackW() + 8, 16)) {
                dragging = i;
                applySlider(i, mx);
                return true;
            }
        }
        pressing = true;
        dragged = false;
        pressX = mx;
        pressY = my;
        return true;
    }

    /** Thả chuột mà không kéo đi xa thì coi là một lần chạm */
    private void tap(double mx, double my) {
        ElaraConfig.Layout l = ElaraConfig.layout(feature);
        for (int i = 0; i < rows.size(); i++) {
            Ctl c = rows.get(i);
            if (c.choices() != null && inside(mx, my, px + PAD - 4, rowY(i), trackW() + 8, ROW_H - 2)) {
                int next = (c.get().get(l) + 1) % c.choices().length;
                c.set().set(l, next);
                ElaraConfig.save();
                return;
            }
        }
        if (hasColor(feature) && inside(mx, my, px + PAD - 4, rowY(rows.size()), trackW() + 8, ROW_H - 2)) {
            MinecraftClient.getInstance().setScreen(new ColorPickerScreen(this, feature));
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        scroll = clampScroll(scroll - (int) Math.round(verticalAmount * 14));
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging >= 0) {
            applySlider(dragging, mx);
            return true;
        }
        if (pressing && button == 0) {
            if (Math.abs(my - pressY) > DRAG_SLOP) dragged = true;
            if (dragged) {
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
        if (dragging >= 0) {
            dragging = -1;
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
        Ctl c = rows.get(i);
        float frac = (float) Math.max(0, Math.min(1, (mx - trackX()) / (double) trackW()));
        int v = c.min() + Math.round(frac * (c.max() - c.min()));
        c.set().set(ElaraConfig.layout(feature), v);
    }

    private static float fraction(int v, int min, int max) {
        return Math.max(0f, Math.min(1f, (v - min) / (float) (max - min)));
    }

    private String trim(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        String out = s;
        while (out.length() > 1 && textRenderer.getWidth(out + "..") > maxPx) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
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
