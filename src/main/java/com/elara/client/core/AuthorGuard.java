package com.elara.client.core;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Kiểm tra tên tác giả trong fabric.mod.json khi khởi động game.
 * Chỉ lưu SHA-256 của tên, không lưu chữ "Lan Anh" dạng thuần trong class.
 *
 * Lưu ý: đây là cơ chế làm khó, không phải bảo vệ tuyệt đối. Ai decompile được jar
 * vẫn có thể gỡ đoạn kiểm tra này. Jar không bị làm khó đọc, nên kiểm tra này chỉ là lớp chặn thông thường.
 */
public final class AuthorGuard {
    public static final String MOD_ID = "elaraclient";

    /** SHA-256 (hex, chữ thường) của tên tác giả "Lan Anh" */
    private static final String EXPECTED_SHA256 = "f27c61d52b11703f6e10a67a4275470fe200cd448d5f0b5aeb7fb9a894d95834";

    private static volatile Boolean valid;

    private AuthorGuard() {}

    public static boolean isValid() {
        Boolean v = valid;
        if (v == null) {
            v = check();
            valid = v;
            if (!v) {
                System.err.println("[Elara] Tên tác giả trong fabric.mod.json đã bị thay đổi. Các tính năng Elara đã bị tắt.");
            }
        }
        return v;
    }

    private static boolean check() {
        try {
            return FabricLoader.getInstance().getModContainer(MOD_ID)
                    .map(c -> c.getMetadata().getAuthors().stream()
                            .anyMatch(p -> EXPECTED_SHA256.equals(sha256Hex(p.getName()))))
                    .orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
