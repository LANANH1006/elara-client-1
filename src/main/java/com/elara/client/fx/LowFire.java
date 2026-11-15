package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;

/**
 * Lửa cháy góc nhìn thứ nhất được hạ thấp xuống dưới tầm mắt và nhuộm màu theo Bảng màu.
 * Không vẽ thêm dải gradient lên HUD nữa. Mixin renderFireOverlay đẩy ma trận xuống theo độ hạ
 * và đặt màu lửa trước khi game vẽ lớp lửa gốc, rồi khôi phục sau đó.
 * require = 0 trong mixin: nếu chữ ký không khớp thì lửa gốc vẫn hiện bình thường.
 */
public final class LowFire {
    private LowFire() {}

    public static boolean enabled() {
        return ElaraConfig.isOn(Feature.LOW_FIRE);
    }

    /** Độ hạ lửa trong không gian khung nhìn, từ 0.0 (không hạ) đến 1.0 */
    public static float offset() {
        int v = ElaraConfig.layout(Feature.LOW_FIRE).get("drop", 45);
        return Math.max(0, Math.min(100, v)) / 100f;
    }

    /** Màu lửa đang chọn, kể cả khi Chroma đang chạy */
    public static int tint() {
        return ElaraConfig.color(Feature.LOW_FIRE);
    }
}
