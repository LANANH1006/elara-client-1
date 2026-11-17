package com.elara.client.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cầu dao cho từng đoạn mã chạy mỗi khung hình hoặc mỗi tick (render, HUD, mixin, tick).
 *
 * Đoạn nào ném lỗi thì ghi stacktrace đúng một lần, rồi tắt đoạn đó đến khi khởi động lại.
 * Các đoạn khác vẫn chạy bình thường. Nhờ vậy một lỗi không lặp lại 60 lần mỗi giây, không in spam
 * ra System.out, và máy không nóng lên vì ghi log liên tục.
 * OutOfMemoryError vẫn được văng ra qua Guard.rethrowIfFatal.
 */
public final class Fault {

    private static final Set<String> OFF = ConcurrentHashMap.newKeySet();

    private Fault() {}

    /** true nếu đoạn này đã bị tắt vì lỗi */
    public static boolean off(String key) {
        return OFF.contains(key);
    }

    /** Chạy một đoạn không trả về giá trị. Lỗi thì tắt đoạn, không ném ra ngoài. */
    public static void run(String key, Runnable body) {
        if (OFF.contains(key)) return;
        try {
            body.run();
        } catch (Throwable t) {
            fail(key, t);
        }
    }

    /** Chạy một đoạn có giá trị trả về. Lỗi thì tắt đoạn và trả về fallback. */
    public static <T> T call(String key, Supplier<T> body, T fallback) {
        if (OFF.contains(key)) return fallback;
        try {
            return body.get();
        } catch (Throwable t) {
            fail(key, t);
            return fallback;
        }
    }

    /** Ghi lỗi của một đoạn. Lần đầu: in stacktrace và tắt đoạn. Các lần sau: không in gì. */
    public static void fail(String key, Throwable t) {
        Guard.rethrowIfFatal(t);
        if (!OFF.add(key)) return;
        System.out.println("[Elara] Tắt \"" + key + "\" vì lỗi, chỉ ghi một lần: " + t);
        t.printStackTrace(System.out);
        System.out.flush();
    }
}
