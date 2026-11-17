package com.elara.client.core;

/**
 * Nhật ký chẩn đoán cho các bước khởi động và tải tài nguyên.
 *
 * Mỗi dòng được in ra System.out rồi flush ngay, để khi game sập giữa chừng thì dòng cuối vẫn nằm
 * trong latest.log, không bị giữ lại trong bộ đệm của luồng.
 */
public final class Diag {

    /** Mốc khởi động đã in chưa, theo số mốc 1..6 */
    private static final boolean[] BOOT_DONE = new boolean[7];

    private Diag() {}

    public static void log(String step) {
        System.out.println("[Elara] " + step);
        System.out.flush();
    }

    /**
     * In dấu chân "[Elara-Boot] n: nhãn" đúng một lần cho mỗi mốc n (1..6), rồi flush.
     * Chỉ gọi ở các điểm khởi động và ở sảnh chính, không gọi trong tick hay render.
     */
    public static synchronized void boot(int step, String label) {
        if (step < 1 || step >= BOOT_DONE.length || BOOT_DONE[step]) return;
        BOOT_DONE[step] = true;
        System.out.println("[Elara-Boot] " + step + ": " + label);
        System.out.flush();
    }
}
