package com.elara.client.core;

/**
 * Thời gian chờ sau khi vào server hoặc map. Trong JOIN_TICKS tick đầu (khoảng 2 giây), các hiệu ứng nặng
 * (thế giới, bóng mềm, cánh, vũ khí 3D, PvP) tạm dừng, để game tải xong chunk và ổn định mạng trước.
 *
 * Chỉ ghi trên luồng chính; đọc có volatile để luồng khác (nếu có) thấy giá trị mới nhất.
 */
public final class Warmup {
    public static final int JOIN_TICKS = 40;

    private static volatile int left = 0;

    private Warmup() {}

    /** Gọi khi người chơi vừa vào server hoặc map */
    public static void onJoin() {
        left = JOIN_TICKS;
    }

    /** Gọi mỗi tick trên luồng chính */
    public static void tick() {
        if (left > 0) left--;
    }

    /** true khi đã hết thời gian chờ và các hiệu ứng nặng được phép chạy */
    public static boolean ready() {
        return left <= 0;
    }
}
