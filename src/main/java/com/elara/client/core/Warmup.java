package com.elara.client.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/**
 * Hai việc tạm dừng hiệu ứng nặng:
 * 1. Thời gian chờ sau khi vào server/map: JOIN_TICKS tick đầu (khoảng 2 giây) game tải chunk trước.
 * 2. Khi đang mở túi đồ hoặc rương (HandledScreen): không vẽ hiệu ứng 3D phía sau để đỡ tụt FPS.
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

    /** true khi đã hết thời gian chờ (không tính màn hình túi đồ) */
    public static boolean ready() {
        return left <= 0;
    }

    /** true khi được phép chạy hiệu ứng nặng: đã hết thời gian chờ và không đang mở túi đồ/rương */
    public static boolean heavy() {
        if (left > 0) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || !(client.currentScreen instanceof HandledScreen<?>);
    }
}
