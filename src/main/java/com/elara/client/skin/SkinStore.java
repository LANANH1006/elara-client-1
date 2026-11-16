package com.elara.client.skin;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Guard;
import com.elara.client.i18n.Lang;
import com.mojang.blaze3d.systems.RenderSystem;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Skin tùy chỉnh của người chơi local.
 *
 * Nguồn sự thật là một mảng ARGB 64x64 trong bộ nhớ. Mỗi khi có đổi, mảng được xuất ra (bỏ các bộ phận đang
 * ẩn) rồi đẩy lên một NativeImageBackedTexture. Việc ghi PNG và gọi API Mojang chạy ở luồng phụ, còn kết
 * quả được đưa về luồng chính ở tickMain. Nhờ vậy không có chữ nào chạm GPU hay mạng trên luồng render.
 *
 * Ẩn một bộ phận = xóa các vùng pixel của bộ phận đó (alpha 0). Cách này đơn giản và đúng với mọi kiểu
 * hiển thị, vì skin là một texture.
 */
public final class SkinStore {
    public static final int SIZE = 64;
    public static final Identifier TEXTURE = Identifier.of("elaraclient", "runtime/custom_skin");
    public static final String[] PARTS = {"head", "body", "larm", "rarm", "lleg", "rleg", "outer"};

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("elara-custom-skin.png");
    /** Màu da mặc định khi chưa có skin nào, để người chơi có chỗ bắt đầu vẽ */
    private static final int DEFAULT_SKIN = 0xFFE0AC69;
    private static final int MAX_JSON = 256 * 1024;
    private static final int MAX_PNG = 2 * 1024 * 1024;

    private static final int[] EDIT = new int[SIZE * SIZE];
    private static int[] fetched;
    private static boolean fileChecked;
    /** Đã có skin được lưu hoặc tải về, nên mới áp vào game */
    private static boolean saved;
    /** Có đổi cần đẩy lên texture */
    private static volatile boolean dirty = true;
    /** Có nét vẽ chưa được ghi ra file */
    private static boolean edited;
    private static NativeImageBackedTexture tex;
    /** Texture đã tạo và upload thành công trên render thread, nên có thể dùng trong game */
    private static volatile boolean uploaded;
    /** Đã đặt lịch đẩy texture lên render thread, tránh xếp hàng nhiều lần */
    private static volatile boolean uploadQueued;
    /** Lỗi khi tạo texture: tắt skin tùy chỉnh để không báo lỗi mỗi khung hình */
    private static volatile boolean failed;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "elara-skin-io");
        t.setDaemon(true);
        return t;
    });
    private static volatile int[] pendingFetch;
    private static volatile boolean pendingSlim;
    private static volatile String pendingName = "";
    private static volatile String message = "";
    private static volatile boolean busy;

    private SkinStore() {}

    // ---------- trạng thái ----------

    public static String message() { return message; }
    public static boolean busy() { return busy; }

    /** Bộ phận: head, body, larm, rarm, lleg, rleg hoặc outer (lớp mũ, áo, tay áo, quần) */
    public static String[] parts() { return PARTS; }

    /**
     * Texture đang dùng trong game, hoặc null nếu chưa lưu skin nào, đã tắt, hoặc texture chưa sẵn sàng.
     * Gọi được từ mọi luồng: không tạo texture tại đây, chỉ đặt lịch lên render thread.
     */
    public static Identifier activeTexture() {
        if (failed || !ElaraConfig.skinOn()) return null;
        ensureLoaded();
        if (!saved) return null;
        if (!uploaded || dirty) requestUpload();
        return uploaded ? TEXTURE : null;
    }

    /** Gọi trên luồng chính mỗi tick: đẩy texture nếu có đổi, và nhận ảnh tải về */
    public static void tickMain() {
        int[] px = pendingFetch;
        if (px != null) {
            pendingFetch = null;
            System.arraycopy(px, 0, EDIT, 0, EDIT.length);
            fetched = px.clone();
            ElaraConfig.setSkinSlim(pendingSlim);
            ElaraConfig.setSkinName(pendingName);
            saved = true;
            dirty = true;
            edited = false;
            message = String.format(Lang.get("skin.fetched"), pendingName);
            saveAsync();
        }
        if (dirty && uploaded) requestUpload();
    }

    /** Đảm bảo texture cập nhật trước khi màn hình vẽ canvas */
    public static void refresh() {
        ensureLoaded();
        if (!uploaded || dirty) requestUpload();
    }

    // ---------- bộ phận ----------

    /** Các vùng pixel của bộ phận: (x0, y0, x1, y1) trên ảnh 64x64 */
    static int[][] rectsOf(String part) {
        return switch (part) {
            case "head" -> new int[][] {{0, 0, 32, 16}, {32, 0, 64, 16}};
            case "body" -> new int[][] {{16, 16, 40, 32}, {16, 32, 40, 48}};
            case "rarm" -> new int[][] {{40, 16, 56, 32}, {40, 32, 56, 48}};
            case "larm" -> new int[][] {{32, 48, 48, 64}, {48, 48, 64, 64}};
            case "rleg" -> new int[][] {{0, 16, 16, 32}, {0, 32, 16, 48}};
            case "lleg" -> new int[][] {{16, 48, 32, 64}, {0, 48, 16, 64}};
            case "outer" -> new int[][] {
                    {32, 0, 64, 16}, {16, 32, 40, 48}, {40, 32, 56, 48},
                    {48, 48, 64, 64}, {0, 32, 16, 48}, {0, 48, 16, 64}};
            default -> new int[0][];
        };
    }

    public static boolean hidden(String part) {
        return ElaraConfig.skinHidden(part);
    }

    /** Ẩn hoặc hiện một bộ phận. Có hiệu lực ngay trên texture và trong game. */
    public static void toggleHidden(String part) {
        ElaraConfig.setSkinHidden(part, !ElaraConfig.skinHidden(part));
        dirty = true;
    }

    /** Chép một bộ phận từ skin vừa tải về vào bản đang vẽ */
    public static void copyPart(String part) {
        if (fetched == null) {
            message = Lang.get("skin.no_fetch");
            return;
        }
        for (int[] r : rectsOf(part)) {
            for (int y = r[1]; y < r[3]; y++) {
                for (int x = r[0]; x < r[2]; x++) {
                    EDIT[y * SIZE + x] = fetched[y * SIZE + x];
                }
            }
        }
        markEdited();
    }

    // ---------- nét vẽ ----------

    public static int pixel(int x, int y) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return 0;
        return EDIT[y * SIZE + x];
    }

    public static void paint(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        int i = y * SIZE + x;
        if (EDIT[i] == argb) return;
        EDIT[i] = argb;
        markEdited();
    }

    /** Đổ màu vùng liền có cùng màu với điểm chạm, bằng ngăn xếp tự quản lý */
    public static void floodFill(int sx, int sy, int argb) {
        if (sx < 0 || sy < 0 || sx >= SIZE || sy >= SIZE) return;
        int target = EDIT[sy * SIZE + sx];
        if (target == argb) return;
        int[] stack = new int[SIZE * SIZE * 4 + 1];
        int sp = 0;
        stack[sp++] = sy * SIZE + sx;
        while (sp > 0) {
            int i = stack[--sp];
            if (EDIT[i] != target) continue;
            EDIT[i] = argb;
            int x = i % SIZE;
            int y = i / SIZE;
            if (x > 0) stack[sp++] = i - 1;
            if (x < SIZE - 1) stack[sp++] = i + 1;
            if (y > 0) stack[sp++] = i - SIZE;
            if (y < SIZE - 1) stack[sp++] = i + SIZE;
        }
        markEdited();
    }

    private static void markEdited() {
        dirty = true;
        edited = true;
    }

    // ---------- lưu, tải ----------

    /** Lưu ngay ở luồng phụ, rồi áp vào game */
    public static void save() {
        saved = true;
        saveAsync();
    }

    /** Khi đóng màn hình skin: lưu nếu có nét vẽ chưa ghi */
    public static void saveIfEdited() {
        if (edited) save();
    }

    /** Khi game tắt: ghi đồng bộ để không mất nét vẽ cuối cùng */
    public static void flushNow() {
        if (!edited) return;
        try {
            writePng(exportPixels());
            edited = false;
        } catch (IOException e) {
            System.err.println("[Elara] Không lưu được skin: " + e.getMessage());
        }
    }

    private static void saveAsync() {
        int[] out = exportPixels();
        edited = false;
        IO.execute(() -> {
            try {
                writePng(out);
                message = Lang.get("skin.saved");
            } catch (IOException e) {
                message = Lang.get("skin.save_fail");
            }
        });
    }

    private static void writePng(int[] px) throws IOException {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, SIZE, SIZE, px, 0, SIZE);
        Files.createDirectories(FILE.getParent());
        ImageIO.write(img, "png", FILE.toFile());
    }

    /** Lần đầu dùng: đọc file đã lưu, hoặc đặt màu da mặc định. Chỉ làm một lần. */
    public static void ensureLoaded() {
        if (fileChecked) return;
        fileChecked = true;
        Arrays.fill(EDIT, DEFAULT_SKIN);
        for (int[] r : rectsOf("outer")) clear(EDIT, r);
        File f = FILE.toFile();
        if (!f.isFile()) return;
        try {
            int[] px = toSquare(ImageIO.read(f));
            if (px != null) {
                System.arraycopy(px, 0, EDIT, 0, EDIT.length);
                saved = true;
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[Elara] Không đọc được skin đã lưu: " + e.getMessage());
        }
    }

    /** Tải skin theo tên người chơi Minecraft: Mojang lấy UUID, rồi lấy hồ sơ có skin. Chạy ở luồng phụ. */
    public static void fetch(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            message = Lang.get("skin.not_found");
            return;
        }
        message = Lang.get("skin.fetching");
        busy = true;
        IO.execute(() -> {
            try {
                JsonObject who = getJson("https://api.mojang.com/users/profiles/minecraft/" + name);
                String id = who.get("id").getAsString();
                JsonObject profile = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
                String b64 = profile.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
                String json = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
                if (textures == null || !textures.has("SKIN")) {
                    message = Lang.get("skin.not_found");
                    return;
                }
                JsonObject skin = textures.getAsJsonObject("SKIN");
                boolean slim = skin.has("metadata")
                        && skin.getAsJsonObject("metadata").has("model")
                        && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
                byte[] png = getBytes(skin.get("url").getAsString(), MAX_PNG);
                int[] px = toSquare(ImageIO.read(new ByteArrayInputStream(png)));
                if (px == null) {
                    message = Lang.get("skin.fetch_fail");
                    return;
                }
                pendingSlim = slim;
                pendingName = name;
                pendingFetch = px;
            } catch (FileNotFoundException e) {
                message = Lang.get("skin.not_found");
            } catch (Exception e) {
                System.err.println("[Elara] Không tải được skin: " + e);
                message = Lang.get("skin.fetch_fail");
            } finally {
                busy = false;
            }
        });
    }

    private static JsonObject getJson(String url) throws IOException {
        byte[] body = getBytes(url, MAX_JSON);
        return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Tải tối đa limit byte. Mojang trả 404 hoặc 204 khi không có người chơi đó. */
    private static byte[] getBytes(String url, int limit) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(8_000);
        c.setReadTimeout(12_000);
        c.setRequestProperty("User-Agent", "ElaraClient");
        int code = c.getResponseCode();
        if (code == 404 || code == 204) throw new FileNotFoundException(url);
        if (code >= 400) throw new IOException("HTTP " + code);
        try (InputStream in = c.getInputStream()) {
            return in.readNBytes(limit);
        }
    }

    /** Đưa ảnh về 64x64 ARGB. Skin cũ 64x32 thì phần dưới để trong suốt. */
    static int[] toSquare(BufferedImage img) {
        if (img == null) return null;
        int w = img.getWidth();
        int h = img.getHeight();
        int[] src = img.getRGB(0, 0, w, h, null, 0, w);
        if (w == SIZE && h == SIZE) return src;
        int[] out = new int[SIZE * SIZE];
        if (w == SIZE && h == SIZE / 2) {
            System.arraycopy(src, 0, out, 0, src.length);
            return out;
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                out[y * SIZE + x] = src[(y * h / SIZE) * w + (x * w / SIZE)];
            }
        }
        return out;
    }

    // ---------- texture ----------

    /** Ảnh xuất ra: bản đang vẽ, với các bộ phận đang ẩn bị xóa */
    static int[] exportPixels() {
        int[] out = EDIT.clone();
        for (String p : PARTS) {
            if (!ElaraConfig.skinHidden(p)) continue;
            for (int[] r : rectsOf(p)) clear(out, r);
        }
        return out;
    }

    private static void clear(int[] px, int[] r) {
        for (int y = r[1]; y < r[3]; y++) {
            for (int x = r[0]; x < r[2]; x++) {
                px[y * SIZE + x] = 0;
            }
        }
    }

    /**
     * Đưa việc tạo hoặc cập nhật texture lên render thread. Đang ở render thread thì làm ngay,
     * còn không thì xếp vào hàng đợi của RenderSystem và làm ở khung hình kế tiếp.
     */
    private static void requestUpload() {
        if (failed) return;
        if (RenderSystem.isOnRenderThread()) {
            pushTexture();
            return;
        }
        if (uploadQueued) return;
        uploadQueued = true;
        RenderSystem.recordRenderCall(() -> {
            uploadQueued = false;
            pushTexture();
        });
    }

    /** Chỉ gọi trên render thread: tạo texture một lần, ghi pixel và upload. Lỗi thì tắt skin, không làm sập. */
    private static void pushTexture() {
        if (failed || !RenderSystem.isOnRenderThread()) return;
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.getTextureManager() == null) return;
            if (tex == null) {
                tex = new NativeImageBackedTexture(new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false));
                c.getTextureManager().registerTexture(TEXTURE, tex);
            }
            NativeImage img = tex.getImage();
            if (img == null) return;
            int[] out = exportPixels();
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    img.setColorArgb(x, y, out[y * SIZE + x]);
                }
            }
            tex.upload();
            dirty = false;
            uploaded = true;
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            failed = true;
            dirty = false;
            System.err.println("[Elara] Không đưa được skin lên GPU, tắt skin tùy chỉnh: " + t);
        }
    }
}
