package com.elara.client.ui;

/**
 * Ô nhập chữ đơn giản. Màn hình chuyển sự kiện char và phím vào đây, nên ô chạy được với bàn phím ảo
 * của điện thoại (sự kiện char đi qua GLFW chứ không qua widget của game).
 */
public final class TextBox {
    private final int maxLen;
    private final StringBuilder text = new StringBuilder();
    private boolean focused;

    public TextBox(int maxLen) {
        this.maxLen = maxLen;
    }

    public String text() {
        return text.toString();
    }

    public void setText(String s) {
        text.setLength(0);
        if (s == null) return;
        text.append(s.length() > maxLen ? s.substring(0, maxLen) : s);
    }

    public boolean focused() {
        return focused;
    }

    public void setFocused(boolean f) {
        focused = f;
    }

    /** Thêm một ký tự in được, bỏ qua ký tự điều khiển */
    public void append(char c) {
        if (c < 32 || c == 127 || text.length() >= maxLen) return;
        text.append(c);
    }

    /** Thêm cả một đoạn, dùng cho dán từ clipboard */
    public void appendAll(String s) {
        if (s == null) return;
        for (int i = 0; i < s.length(); i++) append(s.charAt(i));
    }

    public void backspace() {
        if (text.length() > 0) text.setLength(text.length() - 1);
    }

    /** Con trỏ nhấp nháy, chuỗi rỗng khi đang tắt nhịp */
    public String caret() {
        return (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "";
    }
}
