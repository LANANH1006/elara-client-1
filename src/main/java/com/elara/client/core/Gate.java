package com.elara.client.core;

import net.minecraft.client.MinecraftClient;

/**
 * Cổng chung cho mọi hook render và tick của Elara.
 *
 * Khi Minecraft đang vẽ màn hình tải (thanh Mojang), getOverlay() khác null. Lúc đó mọi hook phải thoát
 * ngay: không chạm render, texture, OpenAL hay javax.sound. Đây là điều kiện để không treo ở 95%.
 */
public final class Gate {
    private Gate() {}

    /** true nếu đang ở màn hình tải của Minecraft, hoặc chưa có client */
    public static boolean loading() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c == null || c.getOverlay() != null;
    }
}
