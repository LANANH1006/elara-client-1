package com.elara.client.core;

/**
 * Nhật ký chẩn đoán cho các bước khởi động và tải tài nguyên.
 *
 * Mỗi dòng được in ra System.out rồi flush ngay, để khi game sập giữa chừng thì dòng cuối vẫn nằm
 * trong latest.log, không bị giữ lại trong bộ đệm của luồng.
 */
public final class Diag {

    private Diag() {}

    public static void log(String step) {
        System.out.println("[Elara] " + step);
        System.out.flush();
    }
}
