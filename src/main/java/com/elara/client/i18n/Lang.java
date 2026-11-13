package com.elara.client.i18n;

import java.util.Map;

/** Chuỗi giao diện bằng Tiếng Việt (vi) và English (en) */
public final class Lang {
    public enum Language { VI, EN }

    private static Language current = Language.VI;

    private static final Map<String, String[]> TEXT = Map.ofEntries(
            e("tab.fix_lag", "Fix Lag", "Fix Lag"),
            e("tab.effects", "Hiệu ứng", "Effects"),
            e("tab.knives", "Dao", "Knives"),
            e("tab.crosshair", "Tâm ngắm", "Crosshair"),
            e("tab.misc", "Tiện ích", "Utilities"),
            e("tab.pack", "Gói", "Pack"),

            e("feature.lag_render", "Giới hạn khoảng cách render", "Limit render distance"),
            e("feature.lag_entities", "Ẩn entity xa và sau tường", "Hide distant and occluded entities"),
            e("feature.lag_particles", "Ẩn particle xa và sau tường", "Hide distant and occluded particles"),
            e("feature.lag_gpu_saver", "Tiết kiệm GPU (Android)", "GPU saver (Android)"),
            e("feature.rain_3d", "Mưa 3D", "3D rain"),
            e("feature.virtual_wings", "Cánh ảo", "Virtual wings"),
            e("feature.jump_ring", "Vòng nhảy pixel", "Pixel jump ring"),
            e("feature.head_accessory", "Phụ kiện đầu", "Head accessory"),
            e("feature.knife_anim", "Hoạt ảnh dao kiểu CS2", "CS2-style knife animation"),
            e("feature.pixel_crosshair", "Tâm ngắm pixel", "Pixel crosshair"),
            e("feature.pvp_reach", "Vòng tầm đánh PvP", "PvP reach circle"),
            e("feature.hit_effect", "Hiệu ứng đánh trúng", "Hit effect"),
            e("feature.music_player", "Trình phát nhạc", "Music player"),
            e("feature.lyrics_island", "Lời bài hát trên đảo", "Lyrics on the island"),
            e("feature.keystrokes", "Hiển thị phím", "Keystrokes"),
            e("feature.sysinfo", "Thông số hệ thống", "System info"),
            e("sys.fps", "FPS", "FPS"),
            e("sys.ping", "Ping", "Ping"),
            e("sys.pos", "Tọa độ", "Position"),
            e("sys.facing", "Hướng", "Facing"),
            e("sys.armor", "Giáp", "Armor"),

            e("ui.language", "Ngôn ngữ", "Language"),
            e("ui.island_editor", "Kéo thả Dynamic Island", "Drag Dynamic Island"),
            e("ui.island_height", "Chiều cao đảo", "Island height"),
            e("ui.island_offset", "Vị trí từ trên xuống", "Offset from top"),
            e("ui.pending", "Hoàn thiện ở Phase ", "Coming in Phase "),
            e("ui.editor_hint", "Kéo đảo tới vị trí mong muốn. Nhấn ESC để lưu.",
                    "Drag the island to where you want it. Press ESC to save."),
            e("ui.reset", "Đặt lại vị trí", "Reset position"),

            e("ui.gc", "Dọn RAM (System.gc)", "Clean RAM (System.gc)"),
            e("ui.gc_hint", "Có thể làm game đứng vài trăm ms", "May freeze the game for a moment"),
            e("ui.gc_done", "Đã giải phóng ", "Freed "),
            e("ui.gc_wait", "Chờ ", "Wait "),
            e("ui.tampered", "Tên tác giả đã bị sửa: tính năng Elara đã tắt",
                    "Author name was changed: Elara features disabled"),

            e("set.x", "Vị trí X", "X position"),
            e("set.y", "Vị trí Y", "Y position"),
            e("set.scale", "Tỉ lệ", "Scale"),
            e("set.color", "Màu", "Color"),
            e("set.shape", "Kiểu", "Shape"),
            e("set.length", "Độ dài", "Length"),
            e("set.thickness", "Độ dày", "Thickness"),
            e("set.gap", "Khe hở", "Gap"),
            e("shape.plus", "Dấu cộng", "Plus"),
            e("shape.dot", "Chấm tròn", "Dot"),
            e("shape.x", "Chữ X", "X"),
            e("shape.circle", "Vòng tròn", "Circle")
    );

    private Lang() {}

    private static Map.Entry<String, String[]> e(String key, String vi, String en) {
        return Map.entry(key, new String[] {vi, en});
    }

    public static String get(String key) {
        String[] pair = TEXT.get(key);
        if (pair == null) return key;
        return current == Language.VI ? pair[0] : pair[1];
    }

    public static Language current() {
        return current;
    }

    public static void toggle() {
        current = current == Language.VI ? Language.EN : Language.VI;
    }

    public static void setCode(String code) {
        current = "en".equalsIgnoreCase(code) ? Language.EN : Language.VI;
    }

    public static String code() {
        return current == Language.VI ? "vi" : "en";
    }
}
