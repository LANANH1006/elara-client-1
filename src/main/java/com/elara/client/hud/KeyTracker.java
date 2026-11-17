package com.elara.client.hud;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Deque;

/** Theo dõi chuột trái/phải đang giữ và đếm số lần nhấn trong 1 giây (CPS). Gọi mỗi tick. */
public final class KeyTracker {
    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();
    private static boolean lastLeft;
    private static boolean lastRight;

    private KeyTracker() {}

    public static void tick(MinecraftClient client) {
        if (client.options == null) return;
        long now = System.currentTimeMillis();
        boolean left = client.options.attackKey.isPressed();
        boolean right = client.options.useKey.isPressed();
        if (left && !lastLeft) LEFT.addLast(now);
        if (right && !lastRight) RIGHT.addLast(now);
        lastLeft = left;
        lastRight = right;
        trim(LEFT, now);
        trim(RIGHT, now);
    }

    private static void trim(Deque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
    }

    public static int cpsLeft() { return LEFT.size(); }

    public static int cpsRight() { return RIGHT.size(); }
}
