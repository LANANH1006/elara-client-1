package com.elara.client.core;

import com.elara.client.i18n.Lang;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lưu cấu hình vào config/elara-client.json.
 *
 * Lưu: mỗi thay đổi đặt cờ dirty. Sau khoảng 300 ms yên lặng thì ghi (gộp các lần bấm hoặc kéo thanh trượt),
 * và ghi ngay khi màn hình đổi hoặc khi game tắt. Tick chỉ kiểm tra một cờ boolean khi không có đổi.
 * Ghi chạy ở luồng phụ qua file tạm rồi đổi tên, nên game không khựng và file không hỏng giữa chừng.
 */
public final class ElaraConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("elara-client.json");
    private static final Path TMP = PATH.resolveSibling("elara-client.json.tmp");
    private static final int MAX_HISTORY = 8;

    /** Gson đọc/ghi các trường public, nên giữ chúng là public */
    public static final class Data {
        public int islandHeight = 28;   // px, 20..40
        public int islandOffsetY = 6;   // px từ mép trên
        public int islandX = -1;        // px, -1 nghĩa là căn giữa
        public String lang = "vi";
        public Map<String, Boolean> toggles = new LinkedHashMap<>();
        public int toggleVersion = 0;        // 4 = mọi tính năng tắt sẵn, kể cả Chat Touch
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
        public int menuTab = 0;              // chỉ số tab đang mở trong Menu Elara
        public int menuScroll = 0;           // vị trí cuộn của tab đó
        public boolean skinOn = false;        // dùng skin tùy chỉnh trong game
        public boolean skinSlim = false;     // kiểu tay mảnh (Slim)
        public String skinName = "";         // tên người chơi của skin đã tải
        public Map<String, Boolean> skinHidden = new LinkedHashMap<>(); // bộ phận đang ẩn
        public String musicUrl = "";         // link nhạc đang nhập
        public int musicVolume = 80;         // 0..100
        public List<String> musicHistory = new ArrayList<>(); // link đã phát, mới nhất đứng đầu
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

    // ---------- tự động lưu ----------

    /** Một luồng phụ, daemon, để việc ghi đĩa không chạm vào luồng render */
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "elara-config-writer");
        t.setDaemon(true);
        return t;
    });
    private static final Object WRITE_LOCK = new Object();
    /** Số thứ tự bản ghi. Chỉ tăng trên luồng chính. */
    private static long seq = 0;
    /** Số thứ tự của bản đã ghi ra đĩa, đọc và ghi dưới WRITE_LOCK. Bản cũ hơn sẽ bị bỏ qua. */
    private static long written = 0;
    /** Có thay đổi chưa ghi ra đĩa. Chỉ đọc và ghi trên luồng chính. */
    private static boolean dirty = false;
    /** Thời điểm bắt đầu có thay đổi chưa ghi (ms), 0 nếu không có */
    private static long dirtySince = 0L;
    /** Trễ sau thay đổi cuối để gộp nhiều lần bấm hoặc kéo thành một lần ghi */
    private static final long WRITE_DELAY_MS = 300L;

    private ElaraConfig() {}

    public static int islandHeight() { return data.islandHeight; }
    public static int islandOffsetY() { return data.islandOffsetY; }
    public static int islandX() { return data.islandX; }

    public static void setIslandHeight(int v) { data.islandHeight = clamp(v, 20, 40); dirty = true; }
    public static void setIslandOffsetY(int v) { data.islandOffsetY = clamp(v, 0, 2000); dirty = true; }
    public static void setIslandX(int v) { data.islandX = clamp(v, -1, 4000); dirty = true; }

    /** Đánh dấu có thay đổi cần ghi. Việc ghi thật diễn ra khi đổi màn hình hoặc khi game tắt. */
    public static void markDirty() { dirty = true; }

    /** Màu chủ đạo của menu: nút gạt, tab đang chọn, thanh trượt */
    public static int themeAccent() { return data.themeAccent; }

    public static void setThemeAccent(int argb) { data.themeAccent = argb | 0xFF000000; dirty = true; }

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

    /** Đặt khối thay thế. to = null thì quay về khối gốc. */
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

    /** Đổi một mục của cảnh 3D. Tự động lưu ở lần kiểm tra kế tiếp. */
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
        dirty = true;
    }

    /** Bật/tắt. Mọi tính năng đều mặc định tắt. */
    /** Mọi tính năng, lấy một lần để isAnyActive không tạo mảng mới mỗi khung hình */
    private static final Feature[] ALL_FEATURES = Feature.values();

    /** true nếu có ít nhất một tính năng đang bật. Các callback dùng để thoát sớm khi tắt hết. */
    public static boolean isAnyActive() {
        for (Feature f : ALL_FEATURES) {
            if (isOn(f)) return true;
        }
        return false;
    }

    public static boolean isOn(Feature f) {
        return data.toggles.getOrDefault(f.name(), false);
    }

    public static void toggle(Feature f) {
        data.toggles.put(f.name(), !isOn(f));
        dirty = true;
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
            case AMBIENCE -> l.color = 0xFFFFB3D1;
            case RAIN_RIPPLE -> l.color = 0xFF9FD8FF;
            case CHROMA_WORLD -> l.color = 0xFF7FE8FF;
            case MIST_GLASS -> l.color = 0xFFE6F4FF;
            case LYRICS_SKY -> l.color = 0xFFFFFFFF;
            default -> { }
        }
        return l;
    }

    // ---------- trạng thái menu, skin, nhạc ----------

    public static int menuTab() { return data.menuTab; }
    public static int menuScroll() { return data.menuScroll; }

    public static void setMenuState(int tab, int scroll) {
        data.menuTab = Math.max(0, tab);
        data.menuScroll = Math.max(0, scroll);
        dirty = true;
    }

    /** Có dùng skin tùy chỉnh không. Mixin skin kiểm tra điều này trước mọi việc khác. */
    public static boolean hasCustomSkin() { return data.skinOn; }
    public static boolean skinOn() { return data.skinOn; }
    public static void setSkinOn(boolean on) { data.skinOn = on; dirty = true; }
    public static boolean skinSlim() { return data.skinSlim; }
    public static void setSkinSlim(boolean slim) { data.skinSlim = slim; dirty = true; }
    public static String skinName() { return data.skinName == null ? "" : data.skinName; }
    public static void setSkinName(String name) { data.skinName = name == null ? "" : name; dirty = true; }

    /** Bộ phận skin đang bị ẩn: head, body, larm, rarm, lleg, rleg, outer */
    public static boolean skinHidden(String part) {
        return data.skinHidden != null && Boolean.TRUE.equals(data.skinHidden.get(part));
    }

    public static void setSkinHidden(String part, boolean hidden) {
        if (data.skinHidden == null) data.skinHidden = new LinkedHashMap<>();
        data.skinHidden.put(part, hidden);
        dirty = true;
    }

    public static String musicUrl() { return data.musicUrl == null ? "" : data.musicUrl; }
    public static void setMusicUrl(String url) { data.musicUrl = url == null ? "" : url; dirty = true; }
    public static int musicVolume() { return data.musicVolume; }
    public static void setMusicVolume(int v) { data.musicVolume = clamp(v, 0, 100); dirty = true; }

    /** Danh sách link đã phát, mới nhất đứng đầu. Trả về bản sửa được. */
    public static List<String> musicHistory() {
        if (data.musicHistory == null) data.musicHistory = new ArrayList<>();
        return data.musicHistory;
    }

    public static void addMusicHistory(String url) {
        List<String> h = musicHistory();
        h.remove(url);
        h.add(0, url);
        while (h.size() > MAX_HISTORY) h.remove(h.size() - 1);
        dirty = true;
    }

    // ---------- đọc và ghi ----------

    public static void load() {
        if (java.nio.file.Files.exists(PATH)) {
            try (Reader r = Files.newBufferedReader(PATH)) {
                Data loaded = GSON.fromJson(r, Data.class);
                if (loaded != null) data = loaded;
            } catch (IOException | RuntimeException e) {
                System.err.println("[Elara] Không đọc được config, dùng mặc định: " + e.getMessage());
                data = new Data();
            }
        }
        Lang.setCode(data.lang);
        if (data.toggles == null) data.toggles = new LinkedHashMap<>();
        if (data.toggleVersion < 4) {
            // Một lần duy nhất: tắt mọi tính năng (kể cả Chat Touch) và tắt skin tùy chỉnh.
            // Khi vào game chỉ còn phím Right Shift và nút [Elara]. Bật lại trong menu Elara nếu cần.
            data.toggles.clear();
            data.skinOn = false;
            data.toggleVersion = 4;
            dirty = true;
        }
        if (data.hudVersion < 2) {
            // Bản cũ đặt HUD ở giữa trái và đè lên chat: chuyển sang vị trí mới
            if (data.layouts != null) {
                data.layouts.remove(Feature.KEYSTROKES.name());
                data.layouts.remove(Feature.SYSINFO.name());
            }
            data.hudVersion = 2;
        }
    }

    /** Mỗi tick trên luồng chính. Không có đổi thì chỉ kiểm tra một cờ; có đổi và đã yên đủ WRITE_DELAY_MS thì ghi. */
    public static void tick() {
        if (!dirty) {
            dirtySince = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        if (dirtySince == 0L) {
            dirtySince = now;
            return;
        }
        if (now - dirtySince < WRITE_DELAY_MS) return;
        dirtySince = 0L;
        flushIfDirty();
    }

    /** Đánh dấu cần ghi. Ghi thật sau khoảng WRITE_DELAY_MS, khi đổi màn hình, hoặc khi game tắt. */
    public static void save() {
        dirty = true;
    }

    /**
     * Gọi khi màn hình đổi (đóng menu, mở màn hình khác). Chỉ ghi khi có dirty, và ghi đúng một lần
     * rồi xóa cờ. Việc ghi chạy ở luồng phụ. Không được gọi theo tick.
     */
    public static void flushIfDirty() {
        if (!dirty) return;
        dirty = false;
        submit(toJson());
    }

    /** Ghi đồng bộ khi game tắt, để không mất thay đổi cuối cùng */
    public static void flushNow() {
        dirty = false;
        writeIfNewer(++seq, toJson());
    }

    private static String toJson() {
        data.lang = Lang.code();
        return GSON.toJson(data);
    }

    private static void submit(String json) {
        long my = ++seq;
        try {
            WRITER.execute(() -> writeIfNewer(my, json));
        } catch (RuntimeException e) {
            writeIfNewer(my, json);
        }
    }

    /** Chỉ ghi nếu bản này mới hơn bản đã ghi, nên bản cũ xếp hàng sau không đè lên bản mới */
    private static void writeIfNewer(long my, String json) {
        synchronized (WRITE_LOCK) {
            if (my <= written) return;
            try {
                Files.createDirectories(PATH.getParent());
                Files.writeString(TMP, json, StandardCharsets.UTF_8);
                Files.move(TMP, PATH, StandardCopyOption.REPLACE_EXISTING);
                written = my;
            } catch (IOException e) {
                System.err.println("[Elara] Không lưu được config: " + e.getMessage());
            }
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
