package com.elara.client.core;

import com.elara.client.i18n.Lang;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lưu cấu hình vào config/elara-client.json */
public final class ElaraConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("elara-client.json");

    /** Gson đọc/ghi các trường public, nên giữ chúng là public */
    public static final class Data {
        public int islandHeight = 28;   // px, 20..40
        public int islandOffsetY = 6;   // px từ mép trên
        public int islandX = -1;        // px, -1 nghĩa là căn giữa
        public String lang = "vi";
        public Map<String, Boolean> toggles = new LinkedHashMap<>();
        public LagFix.Backup lagBackup; // giá trị gốc của game trước khi Fix Lag can thiệp
        public Map<String, Layout> layouts = new LinkedHashMap<>();
        public int themeAccent = 0xFF0A84FF; // màu chủ đạo của menu (ARGB)
        public int hudVersion = 0;           // 2 = đã áp dụng vị trí HUD mặc định mới
        public int titleMode = 0;            // 0 = ảnh nền tĩnh, 1 = cảnh 3D sống
        public int sceneTime = 1;            // 0 bình minh, 1 trưa, 2 hoàng hôn, 3 đêm
        public int sceneTree = 0;            // 0 anh đào, 1 sồi, 2 thông
        public int sceneCam = 0;             // 0 xoay vòng, 1 trôi nhẹ, 2 đứng yên
        public Map<String, String> blockRemap = new LinkedHashMap<>(); // khối trong bản vẽ -> khối thay thế
        public String schematic = "";        // tên file .litematic đang chọn
        public int[] builderOrigin;          // gốc bản vẽ (x, y, z), null nếu chưa đặt
    }

    /** Vị trí, tỉ lệ và màu của một tính năng. Vị trí tính theo phần trăm màn hình. */
    public static final class Layout {
        public int xPct = 50;
        public int yPct = 5;
        /** Vị trí theo pixel GUI, do HUD Editor ghi. -1 nghĩa là dùng xPct/yPct. */
        public int xPx = -1;
        public int yPx = -1;
        public int scalePct = 100;
        public int color = 0xFFFFFFFF;
        /** Các thông số riêng của tính năng (kiểu, độ dài, ...) */
        public Map<String, Integer> extra = new LinkedHashMap<>();

        public int get(String key, int def) {
            Integer v = extra == null ? null : extra.get(key);
            return v == null ? def : v;
        }

        public void put(String key, int value) {
            if (extra == null) extra = new LinkedHashMap<>();
            extra.put(key, value);
        }
    }

    private static Data data = new Data();

    private ElaraConfig() {}

    public static int islandHeight() { return data.islandHeight; }
    public static int islandOffsetY() { return data.islandOffsetY; }
    public static int islandX() { return data.islandX; }

    public static void setIslandHeight(int v) { data.islandHeight = clamp(v, 20, 40); }
    public static void setIslandOffsetY(int v) { data.islandOffsetY = clamp(v, 0, 2000); }
    public static void setIslandX(int v) { data.islandX = clamp(v, -1, 4000); }

    /** Màu chủ đạo của menu: nút gạt, tab đang chọn, thanh trượt */
    public static int themeAccent() { return data.themeAccent; }

    public static void setThemeAccent(int argb) { data.themeAccent = argb | 0xFF000000; }

    /** Nền màn hình chính: 0 ảnh tĩnh, 1 cảnh 3D */
    public static int titleMode() { return data.titleMode; }
    public static int sceneTime() { return data.sceneTime; }
    public static int sceneTree() { return data.sceneTree; }
    public static int sceneCam() { return data.sceneCam; }

    /** Khối thay thế của một khối trong bản vẽ, hoặc chính nó nếu không đổi */
    public static String remap(String blockId) {
        if (data.blockRemap == null) return blockId;
        String to = data.blockRemap.get(blockId);
        return to == null ? blockId : to;
    }

    /** Đặt khối thay thế. to = null thì quay về khối gốc. Lưu ngay ra config. */
    public static void setRemap(String from, String to) {
        if (data.blockRemap == null) data.blockRemap = new LinkedHashMap<>();
        if (to == null) data.blockRemap.remove(from);
        else data.blockRemap.put(from, to);
        save();
    }

    public static String schematic() { return data.schematic == null ? "" : data.schematic; }

    public static void setSchematic(String name) {
        data.schematic = name == null ? "" : name;
        save();
    }

    /** Gốc bản vẽ (x, y, z), hoặc null nếu chưa đặt */
    public static int[] builderOrigin() { return data.builderOrigin; }

    public static void setBuilderOrigin(int x, int y, int z) {
        data.builderOrigin = new int[] {x, y, z};
        save();
    }

    /** Đổi một mục của cảnh 3D. Lưu luôn ra file để lần sau còn giữ. */
    public static void setScene(String key, int v) {
        switch (key) {
            case "mode" -> data.titleMode = clamp(v, 0, 1);
            case "time" -> data.sceneTime = clamp(v, 0, 3);
            case "tree" -> data.sceneTree = clamp(v, 0, 2);
            case "cam" -> data.sceneCam = clamp(v, 0, 2);
            default -> { return; }
        }
        save();
    }

    /** Đưa một tính năng về vị trí, tỉ lệ, màu mặc định (giữ nguyên bật/tắt) */
    public static void resetLayout(Feature f) {
        if (data.layouts == null) data.layouts = new LinkedHashMap<>();
        data.layouts.put(f.name(), defaultLayout(f));
    }

    public static boolean isOn(Feature f) {
        return data.toggles.getOrDefault(f.name(), false);
    }

    public static void toggle(Feature f) {
        data.toggles.put(f.name(), !isOn(f));
    }

    public static Layout layout(Feature f) {
        if (data.layouts == null) data.layouts = new LinkedHashMap<>();
        return data.layouts.computeIfAbsent(f.name(), k -> defaultLayout(f));
    }

    /** Màu đang dùng: màu đã chọn, hoặc màu cầu vồng tự chạy nếu bật Chroma */
    public static int color(Feature f) {
        Layout l = layout(f);
        if (l.get("chroma", 0) == 1) {
            float hue = (System.currentTimeMillis() % 3000L) / 3000f;
            int rgb = ColorMath.hsbToRgb(hue, 0.8f, 1.0f);
            return (l.color & 0xFF000000) | rgb;
        }
        return l.color;
    }

    /** Vị trí mặc định của từng HUD, để các HUD không chồng lên nhau */
    private static Layout defaultLayout(Feature f) {
        Layout l = new Layout();
        switch (f) {
            // Góc trên trái, không đè khung chat (chat nằm ở góc dưới bên trái)
            case KEYSTROKES -> { l.xPct = 1; l.yPct = 2; l.color = 0xFF0A84FF; }
            case SYSINFO -> { l.xPct = 1; l.yPct = 30; }
            case PIXEL_CROSSHAIR -> { l.xPct = 50; l.yPct = 50; }
            case RAIN_3D -> l.color = 0xFF9FD8FF;
            case WEAPON_3D -> l.color = 0xFF7FE8FF;
            // Target HUD: giữa phải màn hình, không đè HUD phím và thông số
            case TARGET_HUD -> { l.xPct = 70; l.yPct = 42; l.color = 0xFF7FD4FF; }
            case SHIELD_BREAKER -> l.color = 0xFF9EF0FF;
            case HIT_RIPPLE -> l.color = 0xFF9AE6FF;
            case AFTERGLOW -> l.color = 0xFFFFE9A8;
            case SHOCKWAVE -> l.color = 0xFF7FD8FF;
            case LOW_FIRE -> l.color = 0xFFFF7A2E;
            default -> { }
        }
        return l;
    }

    public static LagFix.Backup backup() { return data.lagBackup; }
    public static void setBackup(LagFix.Backup b) { data.lagBackup = b; }

    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader r = Files.newBufferedReader(PATH)) {
                Data loaded = GSON.fromJson(r, Data.class);
                if (loaded != null) data = loaded;
            } catch (IOException | RuntimeException e) {
                System.err.println("[Elara] Không đọc được config, dùng mặc định: " + e.getMessage());
                data = new Data();
            }
        }
        Lang.setCode(data.lang);
        if (data.hudVersion < 2) {
            // Bản cũ đặt HUD ở giữa trái và đè lên chat: chuyển sang vị trí mới
            if (data.layouts != null) {
                data.layouts.remove(Feature.KEYSTROKES.name());
                data.layouts.remove(Feature.SYSINFO.name());
            }
            data.hudVersion = 2;
        }
    }

    public static void save() {
        data.lang = Lang.code();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer w = Files.newBufferedWriter(PATH)) {
                GSON.toJson(data, w);
            }
        } catch (IOException e) {
            System.err.println("[Elara] Không lưu được config: " + e.getMessage());
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
