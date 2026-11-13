package com.elara.client.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * Vẽ biểu tượng quả táo cắn dở tối giản bằng code, không dùng file ảnh.
 * Hình được định nghĩa bằng hàm toán học theo tỉ lệ của kích thước, rồi tô từng dải pixel.
 */
public final class AppleLogo {

    private AppleLogo() {}

    /** cx, cy là tâm; size là chiều cao gần đúng (pixel) */
    public static void draw(DrawContext ctx, int cx, int cy, int size, int color) {
        int half = size / 2;
        for (int py = -half; py < half; py++) {
            double v = (py + 0.5) / size;
            int runStart = Integer.MIN_VALUE;
            for (int px = -half; px <= half; px++) {
                boolean on = px < half && inside((px + 0.5) / size, v);
                if (on && runStart == Integer.MIN_VALUE) {
                    runStart = px;
                } else if (!on && runStart != Integer.MIN_VALUE) {
                    ctx.fill(cx + runStart, cy + py, cx + px, cy + py + 1, color);
                    runStart = Integer.MIN_VALUE;
                }
            }
        }
    }

    /** u, v là tọa độ chuẩn hóa quanh tâm, trong khoảng khoảng -0.5 đến 0.5 */
    private static boolean inside(double u, double v) {
        boolean body = inCircle(u, v, -0.17, 0.05, 0.27)
                || inCircle(u, v, 0.17, 0.05, 0.27)
                || inEllipse(u, v, 0.0, 0.15, 0.40, 0.25);
        boolean bite = inCircle(u, v, 0.42, -0.12, 0.16);
        boolean stem = Math.abs(u) <= 0.025 && v >= -0.34 && v <= -0.14;
        boolean leaf = inEllipse(u, v, 0.14, -0.33, 0.13, 0.07);
        return (body && !bite) || stem || leaf;
    }

    private static boolean inCircle(double u, double v, double cu, double cv, double r) {
        double du = u - cu;
        double dv = v - cv;
        return du * du + dv * dv <= r * r;
    }

    private static boolean inEllipse(double u, double v, double cu, double cv, double ru, double rv) {
        double du = (u - cu) / ru;
        double dv = (v - cv) / rv;
        return du * du + dv * dv <= 1.0;
    }
}
