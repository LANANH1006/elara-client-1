package com.elara.client.core;

/**
 * Dọn RAM bằng System.gc(). Chỉ chạy khi người chơi bấm nút, có thời gian chờ
 * để không bấm liên tục gây giật. Lưu ý: GC đầy đủ thường khiến game đứng vài trăm ms.
 */
public final class MemoryTools {
    private static final long COOLDOWN_MS = 15_000L;
    private static long lastRun = 0L;

    private MemoryTools() {}

    /** Trả về số MB đã giải phóng, hoặc -1 nếu còn trong thời gian chờ */
    public static synchronized long clean() {
        long now = System.currentTimeMillis();
        if (now - lastRun < COOLDOWN_MS) return -1L;
        lastRun = now;

        Runtime rt = Runtime.getRuntime();
        long before = rt.totalMemory() - rt.freeMemory();
        System.gc();
        long after = rt.totalMemory() - rt.freeMemory();
        return Math.max(0L, before - after) / (1024L * 1024L);
    }

    /** Số giây còn lại trước khi được dọn tiếp */
    public static synchronized long cooldownLeftSeconds() {
        long left = COOLDOWN_MS - (System.currentTimeMillis() - lastRun);
        return left <= 0 ? 0L : (left + 999L) / 1000L;
    }
}
