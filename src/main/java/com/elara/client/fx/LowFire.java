package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Lửa cháy trên màn hình góc nhìn thứ nhất, hạ thấp xuống đáy màn hình và đổi màu theo tùy chỉnh.
 * Mixin renderFireOverlay đánh dấu và hủy lớp lửa gốc. Lửa mới chỉ được vẽ khi mixin đã chạy trong
 * khung hình này, nên nếu mixin không khớp với game thì lửa gốc vẫn hiện và không bị mất.
 */
public final class LowFire {
    /** Mốc thời gian lần cuối mixin chạy */
    private static volatile long hookMs = 0L;
    /** Khoảng thời gian coi là cùng một khung hình */
    private static final long FRAME_MS = 120L;

    private LowFire() {}

    public static boolean enabled() {
        return ElaraConfig.isOn(Feature.LOW_FIRE);
    }

    /** Gọi từ mixin, ngay trước khi hủy lớp lửa gốc */
    public static void markHook() {
        hookMs = System.currentTimeMillis();
    }

    /** Vẽ lửa hạ thấp. Gọi trong HUD, sau khi lửa gốc đã bị hủy. */
    public static void render(DrawContext ctx) {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        if (now - hookMs > FRAME_MS) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        ElaraConfig.Layout l = ElaraConfig.layout(Feature.LOW_FIRE);
        int argb = ElaraConfig.color(Feature.LOW_FIRE);
        double pct = Math.max(5, Math.min(60, l.get("height", 22))) / 100.0;
        // Ngọn lửa nhấp nháy nhẹ quanh chiều cao chuẩn
        double flicker = 0.93 + 0.07 * Math.sin(now / 90.0);
        int h = (int) Math.round(sh * pct * flicker);
        if (h <= 0) return;

        // Dải màu: đậm ở mép dưới, mờ dần lên trên
        for (int y = 0; y < h; y += 2) {
            double f = 1.0 - y / (double) h;
            float a = (float) (Math.pow(f, 1.8) * 0.85);
            ctx.fill(0, sh - y - 2, sw, sh - y, WorldBatch.withAlpha(argb, a));
        }
    }
}
