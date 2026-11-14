package com.elara.client.core;

/** Đổi màu HSB và RGB. Viết tay, không dùng java.awt. */
public final class ColorMath {

    private ColorMath() {}

    /** h, s, v đều từ 0 đến 1. Trả về RGB dạng 0xRRGGBB. */
    public static int hsbToRgb(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        float r;
        float g;
        float b;
        if (s <= 0f) {
            r = v;
            g = v;
            b = v;
        } else {
            int i = (int) Math.floor(hh);
            float f = hh - i;
            float p = v * (1f - s);
            float q = v * (1f - s * f);
            float t = v * (1f - s * (1f - f));
            switch (i) {
                case 0 -> { r = v; g = t; b = p; }
                case 1 -> { r = q; g = v; b = p; }
                case 2 -> { r = p; g = v; b = t; }
                case 3 -> { r = p; g = q; b = v; }
                case 4 -> { r = t; g = p; b = v; }
                default -> { r = v; g = p; b = q; }
            }
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    /** Trả về {h, s, v}, mỗi giá trị từ 0 đến 1, từ RGB 0xRRGGBB */
    public static float[] rgbToHsb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0f;
        if (d > 0f) {
            if (max == r) {
                h = (g - b) / d;
            } else if (max == g) {
                h = (b - r) / d + 2f;
            } else {
                h = (r - g) / d + 4f;
            }
            h /= 6f;
            if (h < 0f) h += 1f;
        }
        float s = max <= 0f ? 0f : d / max;
        return new float[] {h, s, max};
    }
}
