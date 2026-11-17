package com.elara.client.core;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cầu dao cho từng đoạn mã chạy mỗi khung hình hoặc mỗi tick (render, HUD, mixin, tick, tính năng).
 *
 * Đoạn nào ném lỗi lần đầu thì: in stacktrace ra log một lần, ghi cùng stacktrace vào
 * config/elara/error.log (ghi ngay, đóng file ngay), rồi tắt đoạn đó đến khi khởi động lại.
 * Các đoạn khác vẫn chạy. Menu Elara hiện một dòng đỏ khi phiên này có đoạn bị tắt vì lỗi.
 * OutOfMemoryError vẫn được văng ra qua Guard.rethrowIfFatal.
 */
public final class Fault {

    /** File lỗi để đọc trên điện thoại: config/elara/error.log */
    private static final Path LOG = FabricLoader.getInstance().getConfigDir()
            .resolve("elara").resolve("error.log");
    /** Quá giới hạn này thì không ghi thêm, để file không phình mãi */
    private static final long MAX_LOG_BYTES = 256L * 1024L;

    private static final Set<String> OFF = ConcurrentHashMap.newKeySet();
    private static volatile String lastKey;
    private static boolean sessionHeaderWritten;

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

    /** Số đoạn đang bị tắt vì lỗi trong phiên này */
    public static int count() {
        return OFF.size();
    }

    /** Tên đoạn bị lỗi gần nhất trong phiên này, hoặc null nếu chưa có lỗi */
    public static String lastKey() {
        return lastKey;
    }

    /**
     * Ghi lỗi của một đoạn. Lần đầu: in stacktrace, ghi error.log, rồi tắt đoạn.
     * Các lần sau của cùng đoạn: không làm gì, để không spam mỗi khung hình.
     */
    public static synchronized void fail(String key, Throwable t) {
        Guard.rethrowIfFatal(t);
        if (!OFF.add(key)) return;
        lastKey = key;
        System.out.println("[Elara] Tắt \"" + key + "\" vì lỗi, chỉ ghi một lần: " + t);
        t.printStackTrace(System.out);
        System.out.flush();
        writeLog(key, t);
    }

    /** Ghi một khối lỗi vào config/elara/error.log. Không bao giờ ném ra ngoài: lỗi ghi file chỉ in ra log. */
    private static void writeLog(String key, Throwable t) {
        try {
            if (Files.exists(LOG) && Files.size(LOG) > MAX_LOG_BYTES) return;
            Files.createDirectories(LOG.getParent());
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            LocalDateTime now = LocalDateTime.now().withNano(0);
            StringBuilder sb = new StringBuilder();
            if (!sessionHeaderWritten) {
                sessionHeaderWritten = true;
                sb.append("\n==== Phiên mới ").append(now).append(" ====\n");
            }
            sb.append("[").append(now).append("] Tắt \"").append(key).append("\"\n").append(sw);
            Files.writeString(LOG, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            System.err.println("[Elara] Không ghi được error.log: " + e);
        }
    }
}
