package com.elara.client.hud;

import com.elara.client.core.Feature;

import java.util.EnumMap;
import java.util.Map;

/**
 * Kích thước thật (pixel GUI, chưa nhân tỉ lệ) của từng khung HUD, ghi lại mỗi lần HUD được vẽ.
 * HUD Editor đọc giá trị này để kéo đúng từng pixel. Trước khi HUD vẽ lần đầu thì dùng số ước lượng.
 */
public final class HudBounds {
    private static final Map<Feature, int[]> SIZES = new EnumMap<>(Feature.class);

    static {
        SIZES.put(Feature.KEYSTROKES, new int[] {58, 90});
        SIZES.put(Feature.SYSINFO, new int[] {120, 60});
    }

    private HudBounds() {}

    public static void record(Feature f, int w, int h) {
        SIZES.put(f, new int[] {w, h});
    }

    public static int width(Feature f) {
        int[] s = SIZES.get(f);
        return s == null ? 60 : s[0];
    }

    public static int height(Feature f) {
        int[] s = SIZES.get(f);
        return s == null ? 60 : s[1];
    }
}
