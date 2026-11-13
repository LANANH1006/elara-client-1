package com.elara.client.ui;

import net.minecraft.client.gui.DrawContext;

/** Bảng màu và hàm vẽ kiểu iOS. Không dùng shader nên "kính mờ" là lớp bán trong suốt. */
public final class IosStyle {
    public static final int GLASS = 0xB81C1C1E;       // nền kính tối, hơi trong
    public static final int GLASS_EDGE = 0x55FFFFFF;  // viền sáng mờ
    public static final int TILE = 0x40FFFFFF;        // nền tab đang chọn
    public static final int ACCENT = 0xFF0A84FF;      // xanh iOS
    public static final int GREEN = 0xFF30D158;       // công tắc bật
    public static final int GRAY = 0xFF3A3A3C;        // công tắc tắt
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFF9A9AA0;

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

    /** Khung kính: viền sáng bên ngoài, nền tối bên trong */
    public static void glassPanel(DrawContext ctx, int x, int y, int w, int h, int radius) {
        rounded(ctx, x - 1, y - 1, w + 2, h + 2, radius + 1, GLASS_EDGE);
        rounded(ctx, x, y, w, h, radius, GLASS);
    }

    /** Công tắc kiểu iPhone */
    public static void toggleSwitch(DrawContext ctx, int x, int y, boolean on) {
        int w = 30;
        int h = 16;
        rounded(ctx, x, y, w, h, h / 2, on ? GREEN : GRAY);
        int knob = h - 4;
        int kx = on ? x + w - knob - 2 : x + 2;
        rounded(ctx, kx, y + 2, knob, knob, knob / 2, 0xFFFFFFFF);
    }

    /** Đổi alpha của một màu RGB, alpha từ 0 đến 1 */
    public static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}
