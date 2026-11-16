package com.elara.client.core;

/**
 * Fix Lag: chỉ tối ưu đường vẽ bên trong mod (bỏ thực thể và block entity ở xa, giảm hạt khi nhiều).
 *
 * Không đụng tới GameOptions. Không đổi giới hạn FPS, VSync, tầm nhìn, đồ họa, mipmap, bóng hay bất kỳ
 * tùy chọn nào trong options.txt: các giá trị đó luôn giữ nguyên như người chơi đã đặt.
 * Mọi tối ưu ở đây chỉ chạy khi người chơi bật tính năng tương ứng trong menu Elara.
 */
public final class LagFix {

    private LagFix() {}

    /** Nhóm tối ưu nội bộ mà nút LAG trên thanh chat bật/tắt cùng lúc */
    private static final Feature[] MAX_SET = {
            Feature.LAG_ENTITIES, Feature.LAG_PARTICLES, Feature.LAG_BLOCK_ENTITIES
    };

    /** Có tính năng nào trong nhóm tối ưu đang bật không (dùng để tô sáng nút LAG) */
    public static boolean maxOn() {
        for (Feature f : MAX_SET) {
            if (ElaraConfig.isOn(f)) return true;
        }
        return false;
    }

    /** Bật cả nhóm, hoặc tắt cả nhóm nếu đang bật. Chỉ đổi cờ của Elara, không chạm vào game. */
    public static void toggleMax() {
        boolean turnOn = !maxOn();
        for (Feature f : MAX_SET) {
            if (ElaraConfig.isOn(f) != turnOn) ElaraConfig.toggle(f);
        }
        Diag.log("LagFix: nhóm tối ưu nội bộ " + (turnOn ? "bật" : "tắt"));
    }
}
