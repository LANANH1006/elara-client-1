package com.elara.client.schematic;

import com.elara.client.core.ElaraConfig;
import com.elara.client.i18n.Lang;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Trạng thái Builder dùng chung: danh sách file, bản vẽ đang mở, gốc đặt và khối thay thế */
public final class BuilderState {

    /** Các khối thay thế thường dùng, bấm vòng qua lần lượt */
    private static final String[] SWAPS = {
            "minecraft:cobblestone", "minecraft:stone", "minecraft:stone_bricks",
            "minecraft:oak_planks", "minecraft:dirt", "minecraft:smooth_stone",
            "minecraft:sand", "minecraft:white_concrete"
    };

    private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("schematics");

    private static List<String> files = new ArrayList<>();
    private static Schematic loaded;
    private static String loadedName = "";
    private static String error = "";
    private static boolean scanned = false;

    private BuilderState() {}

    /** Quét lại thư mục schematics/, rồi mở lại bản vẽ đã chọn lần trước */
    public static void rescan() {
        scanned = true;
        List<String> found = new ArrayList<>();
        try {
            Files.createDirectories(DIR);
            try (Stream<Path> s = Files.list(DIR)) {
                s.map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".litematic"))
                        .sorted()
                        .forEach(found::add);
            }
        } catch (IOException e) {
            System.err.println("[Elara] Không quét được thư mục schematics: " + e.getMessage());
        }
        files = found;
        String want = ElaraConfig.schematic();
        if (want.isEmpty()) {
            loaded = null;
            loadedName = "";
        } else {
            loadFile(want);
        }
    }

    public static List<String> files() {
        ensureScanned();
        return files;
    }

    public static Schematic loaded() {
        ensureScanned();
        return loaded;
    }

    public static String selectedName() {
        ensureScanned();
        return loadedName;
    }

    public static String error() {
        return error;
    }

    /** Chuyển sang file kế tiếp trong danh sách, quay vòng */
    public static void cycleFile() {
        ensureScanned();
        if (files.isEmpty()) return;
        int i = files.indexOf(loadedName);
        select(files.get((i + 1) % files.size()));
    }

    /** Chọn một file và nhớ lại cho lần sau */
    public static void select(String name) {
        ElaraConfig.setSchematic(name);
        loadFile(name);
    }

    private static void loadFile(String name) {
        loadedName = name;
        try {
            loaded = Schematic.load(DIR.resolve(name));
            error = "";
        } catch (IOException | RuntimeException e) {
            loaded = null;
            error = Lang.get("builder.load_fail") + ": " + e.getMessage();
            System.err.println("[Elara] " + error);
        }
    }

    private static void ensureScanned() {
        if (!scanned) rescan();
    }

    /** Gốc bản vẽ là góc nhỏ nhất của bản vẽ trong thế giới, hoặc null nếu chưa đặt */
    public static BlockPos origin() {
        int[] o = ElaraConfig.builderOrigin();
        return o == null || o.length < 3 ? null : new BlockPos(o[0], o[1], o[2]);
    }

    public static void setOrigin(BlockPos p) {
        ElaraConfig.setBuilderOrigin(p.getX(), p.getY(), p.getZ());
    }

    public static List<Schematic.Entry> entries() {
        Schematic s = loaded();
        return s == null ? List.of() : s.entries();
    }

    /**
     * Khối thay thế kế tiếp của một khối gốc: đi qua SWAPS rồi quay về khối gốc (null).
     * Bỏ qua ứng viên trùng chính khối gốc.
     */
    public static String nextSwap(String id) {
        String cur = ElaraConfig.remap(id);
        int idx = -1;
        if (!cur.equals(id)) {
            for (int i = 0; i < SWAPS.length; i++) {
                if (SWAPS[i].equals(cur)) idx = i;
            }
        }
        for (int i = idx + 1; i < SWAPS.length; i++) {
            if (!SWAPS[i].equals(id)) return SWAPS[i];
        }
        return null;
    }
}
