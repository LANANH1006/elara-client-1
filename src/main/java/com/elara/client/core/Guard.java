package com.elara.client.core;

/**
 * Chốt chặn cho mọi khối catch (Throwable) của mod.
 *
 * Mod bắt lỗi để không làm sập game: lỗi trong một hiệu ứng thì tắt riêng hiệu ứng đó.
 * Nhưng OutOfMemoryError là lỗi bộ nhớ thật của máy, bắt lại sẽ che mất và làm game chạy tiếp
 * trong trạng thái hỏng. Nên mọi catch phải gọi rethrowIfFatal trước, để OOM vẫn văng ra đúng.
 */
public final class Guard {
    private Guard() {}

    /** Ném lại OutOfMemoryError. Các lỗi khác thì trả về để khối catch xử lý. */
    public static void rethrowIfFatal(Throwable t) {
        if (t instanceof OutOfMemoryError oom) throw oom;
    }
}
