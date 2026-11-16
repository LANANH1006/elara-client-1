package com.elara.client.ui;

import com.elara.client.core.ElaraConfig;
import net.minecraft.client.gui.DrawContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Bảng màu và hàm vẽ kiểu iOS. Kính mờ là lớp xám bán trong suốt có viền sáng 1px.
 * Không dùng shader blur, nên phần làm mờ cảnh phía sau vẫn chưa có.
 *
 * Số lệnh fill: các dòng liên tiếp có cùng độ lõm góc được gộp thành một lệnh. Hình đặc gộp cả cạnh thẳng.
 * Gradient gộp tối đa GRADIENT_GROUP dòng, màu lấy ở giữa nhóm. Mắt khó thấy khác biệt ở độ chuyển này.
 */
public final class IosStyle {
    public static final int GLASS = 0xB83A3A3C;       // kính xám, alpha 0.72: một lớp, không blur nhiều lớp
    public static final int GLASS_TOP = 0xB8505055;   // tint sáng hơn ở mép trên kính
    public static final int GLASS_EDGE = 0x66FFFFFF;  // viền sáng 1px
    public static final int TILE = 0x40FFFFFF;        // nền tab đang chọn
    public static final int ACCENT = 0xFF0A84FF;      // xanh dương iOS
    public static final int GREEN = 0xFF30D158;       // công tắc bật
    public static final int GRAY = 0xFF3A3A3C;        // công tắc tắt
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFF9A9AA0;

    /** Số dòng tối đa gộp chung một màu gradient */
    private static final int GRADIENT_GROUP = 3;
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

    /** Hình chữ nhật bo góc, màu đặc */
    public static void rounded(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        fillRows(ctx, x, y, w, h, radius, color, color);
    }

    /** Hình bo góc có màu chuyển dọc từ {@code top} xuống {@code bottom} */
    private static void roundedGradient(DrawContext ctx, int x, int y, int w, int h, int radius, int top, int bottom) {
        fillRows(ctx, x, y, w, h, radius, top, bottom);
    }

    /**
     * Tô hình bo góc theo dòng, gộp các dòng liên tiếp cùng độ lõm để giảm số lệnh fill.
     * Màu đặc (top == bottom) gộp không giới hạn số dòng, gradient gộp tối đa GRADIENT_GROUP dòng.
     */
    private static void fillRows(DrawContext ctx, int x, int y, int w, int h, int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        boolean flat = top == bottom;
        int limit = flat ? h : GRADIENT_GROUP;
        int row = 0;
        while (row < h) {
            int inset = insetAt(row, h, r);
            int end = row + 1;
            while (end < h && end - row < limit && insetAt(end, h, r) == inset) end++;
            int x1 = x + inset;
            int x2 = x + w - inset;
            if (x2 > x1) {
                int c = flat ? top : lerpColor(top, bottom, (row + (end - row) * 0.5f) / Math.max(1, h - 1));
                ctx.fill(x1, y + row, x2, y + end, fadeColor(c));
            }
            row = end;
        }
    }

    /** Độ lõm góc của một dòng: 0 ở phần thẳng, lớn hơn 0 ở hai đầu cong */
    private static int insetAt(int row, int h, int r) {
        double cy = row + 0.5;
        double dist = Math.min(cy, h - cy);
        if (dist >= r) return 0;
        return (int) Math.round(r - Math.sqrt((double) r * r - (r - dist) * (r - dist)));
    }

    /**
     * Kính iOS. Không làm mờ nền thật (cần shader riêng), nên dùng: viền sáng 1px
     * sáng ở cạnh trên và mờ dần ở cạnh dưới, thân kính trong suốt có tint sáng phía trên.
     */
    public static void glass(DrawContext ctx, int x, int y, int w, int h, int radius) {
        rounded(ctx, x - 1, y - 1, w + 2, h + 2, radius + 1, GLASS_EDGE);
        rounded(ctx, x, y, w, h, radius, GLASS);
    }

    /** Khung kính lớn (menu, bảng cài đặt) */
    public static void glassPanel(DrawContext ctx, int x, int y, int w, int h, int radius) {
        glass(ctx, x, y, w, h, radius);
    }

    /** Màu chủ đạo của menu, người chơi đổi trong tab Tiện ích */
    public static int accent() {
        return ElaraConfig.themeAccent();
    }

    // ---------- hoạt ảnh mở/đóng menu ----------

    private static final long OPEN_MS = 220L;
    private static final long CLOSE_MS = 160L;
    private static long openAt = System.nanoTime();
    private static long closeAt = 0L;
    /** Độ mờ hiện tại áp cho mọi hình vẽ, 1 là đậm nguyên bản */
    private static float fade = 1f;

    /** Gọi khi mở menu: bắt đầu phóng to nhẹ và hiện dần */
    public static void beginOpen() {
        openAt = System.nanoTime();
        closeAt = 0L;
    }

    /** Gọi khi bấm đóng: menu sẽ mờ dần trước khi thật sự thoát */
    public static void beginClose() {
        if (closeAt == 0L) closeAt = System.nanoTime();
    }

    public static boolean isClosing() {
        return closeAt != 0L;
    }

    /** Hoạt ảnh đóng đã chạy xong, lúc này mới đổi sang màn hình trước */
    public static boolean closeDone() {
        return closeAt != 0L && (System.nanoTime() - closeAt) / 1_000_000L >= CLOSE_MS;
    }

    /** Tiến độ 0..1 của hoạt ảnh (mở: easeOutCubic; đóng: đi từ 1 về 0) */
    public static float openFactor() {
        long now = System.nanoTime();
        if (closeAt != 0L) {
            float t = Math.min(1f, (now - closeAt) / 1_000_000f / CLOSE_MS);
            return 1f - t * t;
        }
        float t = Math.min(1f, (now - openAt) / 1_000_000f / OPEN_MS);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** Đặt độ mờ cho các hình vẽ sau đó. Nhớ đặt lại 1f khi vẽ xong. */
    public static void setFade(float f) {
        fade = Math.max(0f, Math.min(1f, f));
    }

    /** Áp độ mờ hiện tại vào alpha của một màu ARGB */
    public static int fadeColor(int c) {
        if (fade >= 0.999f) return c;
        int a = Math.round(((c >>> 24) & 0xFF) * fade);
        return (a << 24) | (c & 0x00FFFFFF);
    }

    /** Công tắc kiểu iPhone, dùng vị trí làm khóa animation */
    public static void toggleSwitch(DrawContext ctx, int x, int y, boolean on) {
        toggleSwitch(ctx, x, y, on, x + ":" + y);
    }

    /** Công tắc kiểu iPhone có trượt mượt khi bật/tắt. {@code key} phân biệt từng công tắc. */
    public static void toggleSwitch(DrawContext ctx, int x, int y, boolean on, Object key) {
        toggleSwitch(ctx, x, y, on, key, 30, 16);
    }

    /** Công tắc có kích thước tùy chọn, mặc định 30x16 */
    public static void toggleSwitch(DrawContext ctx, int x, int y, boolean on, Object key, int w, int h) {
        float p = progress(key, on);
        rounded(ctx, x, y, w, h, h / 2, lerpColor(GRAY, accent(), p));
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
