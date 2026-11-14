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
    }

    /** Vị trí, tỉ lệ và màu của một tính năng. Vị trí tính theo phần trăm màn hình. */
    public static final class Layout {
        public int xPct = 50;
        public int yPct = 5;
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
            case KEYSTROKES -> { l.xPct = 1; l.yPct = 50; l.color = 0xFF0A84FF; }
            case SYSINFO -> { l.xPct = 1; l.yPct = 72; }
            case PIXEL_CROSSHAIR -> { l.xPct = 50; l.yPct = 50; }
            case RAIN_3D -> l.color = 0xFF9FD8FF;
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
