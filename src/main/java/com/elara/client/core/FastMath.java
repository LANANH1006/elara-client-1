package com.elara.client.core;

/**
 * sin/cos bằng bảng tra 4096 mục, dùng cho code của Elara (hiệu ứng, animation).
 * Lưu ý: MathHelper của Minecraft cũng đã dùng bảng tra, nên lợi ích ở đây chủ yếu
 * là khi gọi hàng nghìn lần mỗi frame cho particle và hiệu ứng.
 */
public final class FastMath {
    private static final int SIZE = 4096;
    private static final int MASK = SIZE - 1;
    private static final float SCALE = (float) (SIZE / (2.0 * Math.PI));
    private static final float[] SIN = new float[SIZE];

    static {
        for (int i = 0; i < SIZE; i++) {
            SIN[i] = (float) Math.sin(i * 2.0 * Math.PI / SIZE);
        }
    }

    private FastMath() {}

    public static float sin(float rad) {
        return SIN[(int) Math.floor(rad * SCALE) & MASK];
    }

    public static float cos(float rad) {
        return sin(rad + (float) (Math.PI / 2.0));
    }
}
