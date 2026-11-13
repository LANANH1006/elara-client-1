package com.elara.client.ui;

import net.minecraft.client.gui.DrawContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Bảng màu và hàm vẽ kiểu iOS. Kính mờ là lớp xám bán trong suốt có viền sáng 1px.
 * Không dùng shader blur, nên phần làm mờ cảnh phía sau vẫn chưa có.
 */
public final class IosStyle {
    public static final int GLASS = 0x9E3A3A3C;       // kính xám, alpha khoảng 0.62
    public static final int GLASS_EDGE = 0x66FFFFFF;  // viền sáng 1px
    public static final int TILE = 0x40FFFFFF;        // nền tab đang chọn
    public static final int ACCENT = 0xFF0A84FF;      // xanh dương iOS
    public static final int GREEN = 0xFF30D158;       // công tắc bật
    public static final int GRAY = 0xFF3A3A3C;        // công tắc tắt
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFF9A9AA0;

    /** Tốc độ trượt của công tắc, càng lớn càng nhanh */
    private static final float SWITCH_SPEED = 12f;

    private static final Map<Object, Anim> ANIMS = new HashMap<>();

    private static final class Anim {
        float value;
        long last;

        Anim(float value, long now) {
            this.value = value;
            this.last = now;
        }
    }

    private IosStyle() {}

    /** Hình chữ nhật bo góc, vẽ bằng cách tô từng dòng pixel */
    public static void rounded(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        for (int row = 0; row < h; row++) {
            double cy = row + 0.5;
            double dist = Math.min(cy, h - cy);
            int inset = 0;
            if (dist < r) {
                inset = (int) Math.round(r - Math.sqrt((double) r * r - (r - dist) * (r - dist)));
            }
            int x1 = x + inset;
            int x2 = x + w - inset;
            if (x2 > x1) ctx.fill(x1, y + row, x2, y + row + 1, color);
        }
    }

    /** Khung kính: viền sáng 1px bên ngoài, nền xám bán trong suốt bên trong */
    public static void glassPanel(DrawContext ctx, int x, int y, int w, int h, int radius) {
        rounded(ctx, x - 1, y - 1, w + 2, h + 2, radius + 1, GLASS_EDGE);
        rounded(ctx, x, y, w, h, radius, GLASS);
    }

    /** Công tắc kiểu iPhone, dùng vị trí làm khóa animation */
    public static void toggleSwitch(DrawContext ctx, int x, int y, boolean on) {
        toggleSwitch(ctx, x, y, on, x + ":" + y);
    }

    /** Công tắc kiểu iPhone có trượt mượt khi bật/tắt. {@code key} phân biệt từng công tắc. */
    public static void toggleSwitch(DrawContext ctx, int x, int y, boolean on, Object key) {
        float p = progress(key, on);
        int w = 30;
        int h = 16;
        rounded(ctx, x, y, w, h, h / 2, lerpColor(GRAY, GREEN, p));
        int knob = h - 4;
        int kx = Math.round(x + 2 + (w - knob - 4) * p);
        rounded(ctx, kx, y + 2, knob, knob, knob / 2, 0xFFFFFFFF);
    }

    /** Tiến độ 0..1 của công tắc, đi dần về trạng thái đích theo thời gian thực */
    public static float progress(Object key, boolean on) {
        long now = System.nanoTime();
        float target = on ? 1f : 0f;
        Anim a = ANIMS.computeIfAbsent(key, k -> new Anim(target, now));
        float dt = Math.min(0.1f, (now - a.last) / 1_000_000_000f);
        a.last = now;
        float step = 1f - (float) Math.exp(-SWITCH_SPEED * dt);
        a.value += (target - a.value) * step;
        if (Math.abs(target - a.value) < 0.001f) a.value = target;
        return a.value;
    }

    /** Trộn hai màu ARGB theo t từ 0 đến 1 */
    public static int lerpColor(int from, int to, float t) {
        int a = lerpChannel(from >>> 24, to >>> 24, t);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    /** Đổi alpha của một màu RGB, alpha từ 0 đến 1 */
    public static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}
