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
            e("shape.circle", "Vòng tròn", "Circle"),
            e("feature.lag_clouds", "Tắt mây", "Disable clouds"),
            e("feature.lag_biome", "Giảm Biome Blend", "Reduce biome blend"),
            e("feature.lag_mipmap", "Giảm Mipmap", "Reduce mipmap"),
            e("feature.lag_focus_fps", "Giới hạn FPS khi không focus", "Limit FPS when unfocused"),
            e("set.view_cap", "Khoảng cách tối đa", "Max view distance"),
            e("set.fps_cap", "Giới hạn FPS", "FPS cap"),
            e("set.entity_pct", "Khoảng cách thực thể", "Entity distance"),
            e("set.biome_radius", "Bán kính Biome Blend", "Biome blend radius"),
            e("set.mipmap", "Mức Mipmap", "Mipmap level"),
            e("set.focus_fps", "FPS khi không focus", "FPS when unfocused"),
            e("set.open_picker", "Bảng màu >", "Color picker >"),
            e("picker.title", "Bảng màu", "Color picker"),
            e("picker.chroma", "Chroma (màu cầu vồng chạy)", "Chroma (rainbow cycle)"),
            e("feature.no_fog", "Tắt sương mù", "No fog"),
            e("feature.no_block_anim", "Tắt hoạt ảnh khối", "No block animations"),
            e("set.hand_x", "Tay X (trái/phải)", "Hand X (left/right)"),
            e("set.hand_y", "Tay Y (lên/xuống)", "Hand Y (up/down)"),
            e("set.hand_z", "Tay Z (gần/xa)", "Hand Z (near/far)"),
            e("set.tilt", "Nghiêng", "Tilt"),
            e("set.density", "Mật độ", "Density"),
            e("set.hit_style", "Kiểu hiệu ứng", "Effect style"),
            e("hit.rays", "Tia sáng", "Rays"),
            e("hit.stars", "Ngôi sao", "Stars"),
            e("hit.hearts", "Trái tim", "Hearts"),
            e("hit.sparks", "Tia lửa", "Sparks"),
            e("music.title", "Trình phát nhạc", "Music player"),
            e("music.open", "Mở trình phát nhạc >", "Open music player >"),
            e("music.empty", "Chưa có bài nào. Thả file .wav vào elara/music rồi Quét lại",
                    "No tracks. Put .wav files in elara/music, then Rescan"),
            e("music.rescan", "Quét lại", "Rescan"),
            e("music.hint", "Hỗ trợ .wav. Lời: file .lrc cùng tên", "Supports .wav. Lyrics: same-name .lrc"),
            e("feature.footstep_aura", "Hào quang bước chân", "Footstep aura"),
            e("feature.sword_trail", "Vệt chém kiếm", "Sword slash trail"),
            e("set.foot_style", "Kiểu hào quang", "Aura style"),
            e("foot.ring", "Vòng dưới chân", "Ring under feet"),
            e("foot.fire", "Đom đóm bay quanh", "Fireflies"),
            e("set.ring_style", "Kiểu vòng", "Ring style"),
            e("set.wing_style", "Kiểu cánh", "Wing style"),
            e("set.size", "Kích thước", "Size"),
            e("set.speed", "Tốc độ", "Speed"),
            e("set.flap", "Tốc độ vỗ", "Flap speed"),
            e("set.vis", "Hiển thị", "Visibility"),
            e("ring.wave", "Sóng đơn", "Single wave"),
            e("ring.double", "Vòng xoay đôi", "Double rotating ring"),
            e("ring.magic", "Lục giác ma thuật", "Magic hexagon"),
            e("wing.angel", "Thiên thần", "Angel"),
            e("wing.dragon", "Rồng / Quỷ", "Dragon / Demon"),
            e("wing.butterfly", "Bướm", "Butterfly"),
            e("wing.cyber", "Cyber / Holo", "Cyber / Holo"),
            e("vis.self", "Chỉ mình tôi", "Only me"),
            e("vis.nearby", "Mọi người gần đó", "Everyone nearby"),
            e("vis.third", "Chỉ góc nhìn thứ 3 (F5)", "Third person only (F5)"),
            e("pack.reload", "Tải lại gói", "Reload packs"),
            e("pack.empty", "Không có gói nào trong resourcepacks", "No packs in resourcepacks"),
            e("pack.up", "Lên", "Up"),
            e("pack.down", "Xuống", "Down"),
            e("pack.hint", "Đổi xong bấm Tải lại để áp dụng", "Press Reload to apply changes")
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
