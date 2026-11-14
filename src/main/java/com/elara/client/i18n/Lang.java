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
            e("feature.lag_gpu_saver", "Tối ưu hiệu năng / Fix lag chuẩn iOS", "Performance boost / iOS-style fix lag"),
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
            e("music.empty", "Thả nhạc vào elara/music rồi Quét lại", "Put music in elara/music, then Rescan"),
            e("music.rescan", "Quét lại", "Rescan"),
            e("music.hint", "Hỗ trợ .wav .mp3 .ogg. Lời: file .lrc cùng tên",
                    "Supports .wav .mp3 .ogg. Lyrics: same-name .lrc"),
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
            e("pack.hint", "Đổi xong bấm Tải lại để áp dụng", "Press Reload to apply changes"),

            e("desc.lag_render", "Giảm số chunk được vẽ, nhẹ máy nhất", "Draw fewer chunks, lightest option"),
            e("desc.lag_entities", "Không vẽ thực thể quá xa hoặc bị tường che", "Skip far or walled-off entities"),
            e("desc.lag_particles", "Không vẽ hạt quá xa hoặc bị tường che", "Skip far or walled-off particles"),
            e("desc.lag_gpu_saver", "Giảm tải GPU, giữ độ nét gốc của game", "Less GPU load, keeps original sharpness"),
            e("desc.lag_clouds", "Tắt mây để giảm việc vẽ nền", "Turn off clouds to cut draw work"),
            e("desc.lag_biome", "Giảm bán kính trộn màu biome", "Shrink the biome blend radius"),
            e("desc.lag_mipmap", "Giảm số mức mipmap của texture", "Lower the texture mipmap levels"),
            e("desc.lag_focus_fps", "Giới hạn FPS khi cửa sổ không focus", "Cap FPS while the window is unfocused"),
            e("desc.no_fog", "Bỏ sương mù xa", "Remove distance fog"),
            e("desc.no_block_anim", "Tắt hoạt ảnh khối như nước, lửa", "Stop animated block textures"),
            e("desc.rain_3d", "Các hạt mưa 3D rơi quanh người chơi", "3D rain drops around the player"),
            e("desc.virtual_wings", "Đôi cánh lông vũ phía sau lưng", "Feather wings on your back"),
            e("desc.jump_ring", "Vòng pixel nở ra khi nhảy", "Pixel ring that expands when you jump"),
            e("desc.head_accessory", "Hào quang vương miện trên đầu", "Glowing halo above your head"),
            e("desc.footstep_aura", "Vòng sáng dưới chân khi đi", "Light ring under your feet"),
            e("desc.knife_anim", "Dao kiểu CS2 xoay khi đổi slot", "CS2-style knife spin on slot change"),
            e("desc.sword_trail", "Vệt sáng khi chém kiếm", "Light trail on sword swings"),
            e("desc.pixel_crosshair", "Tâm ngắm tùy chỉnh hình dạng", "Custom crosshair shape"),
            e("desc.pvp_reach", "Vòng đỏ hiện tầm đánh", "Ring that shows your attack reach"),
            e("desc.hit_effect", "Hiệu ứng khi đánh trúng địch", "Effect when you hit an enemy"),
            e("desc.music_player", "Phát nhạc từ thư mục elara/music", "Play music from elara/music"),
            e("desc.lyrics_island", "Hiện lời bài hát trên đảo", "Show lyrics on the island"),
            e("desc.keystrokes", "Hiện phím WASD, chuột và CPS", "Show WASD, mouse keys and CPS"),
            e("desc.sysinfo", "FPS, ping, tọa độ, hướng, giáp", "FPS, ping, position, facing, armor"),

            e("ui.active", "đang bật", "active"),
            e("ui.reset_defaults", "Đặt lại mặc định", "Reset to defaults"),
            e("ui.preview", "Xem trước", "Preview"),
            e("ui.accent", "Màu chủ đạo menu", "Menu theme accent"),
            e("ui.hud_editor", "Chỉnh vị trí HUD", "Edit HUD position"),
            e("ui.hud_editor_hint", "Kéo các khung HUD tới chỗ bạn muốn. Nhấn ESC để lưu.",
                    "Drag HUD boxes where you want them. Press ESC to save."),
            e("ui.settings", "Cài đặt", "Settings"),
            e("ui.no_options", "Tính năng này chưa có tùy chỉnh", "This feature has no options"),
            e("ui.close", "Đóng", "Close"),
            e("ui.anim_note", "Hiệu ứng kính: không làm mờ nền thật, dùng lớp trong suốt và viền sáng",
                    "Glass effect: no real background blur, uses translucent layers and a light edge")
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
