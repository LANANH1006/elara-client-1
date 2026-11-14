package com.elara.client.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bản vẽ Litematica (.litematic) đã đọc xong. Chỉ giữ tên khối ở từng ô,
 * bỏ qua entity và block entity. Tọa độ tương đối tính từ góc nhỏ nhất của bản vẽ.
 */
public final class Schematic {

    /** Giới hạn số ô để không ngốn hết RAM trên điện thoại */
    private static final long MAX_CELLS = 4_000_000L;
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    /** Một loại khối và số lượng */
    public record Entry(String id, int count) {}

    /** Một vùng của bản vẽ. Dữ liệu ô nằm theo thứ tự y, z, x (x chạy nhanh nhất). */
    private record Region(int minX, int minY, int minZ, int sx, int sy, int sz,
                          String[] palette, int[] cells) {
        String at(int lx, int ly, int lz) {
            int v = cells[(ly * sz + lz) * sx + lx];
            if (v < 0 || v >= palette.length) return null;
            String name = palette[v];
            return name == null || name.isEmpty() || AIR.contains(name) ? null : name;
        }
    }

    private final String name;
    private final List<Region> regions;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int width;
    private final int height;
    private final int depth;
    private final List<Entry> entries;
    private final int total;

    private Schematic(String name, List<Region> regions, int minX, int minY, int minZ,
                      int width, int height, int depth, List<Entry> entries, int total) {
        this.name = name;
        this.regions = regions;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.entries = entries;
        this.total = total;
    }

    /** Đọc một file .litematic (đã nén gzip). Ném IOException nếu file hỏng hoặc quá lớn. */
    public static Schematic load(Path file) throws IOException {
        NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
        NbtCompound regionTags = root.getCompound("Regions");
        if (regionTags.isEmpty()) throw new IOException("không có vùng nào");

        List<Region> regions = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        long cells = 0;
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;

        for (String key : regionTags.getKeys()) {
            NbtCompound r = regionTags.getCompound(key);
            NbtCompound pos = r.getCompound("Position");
            NbtCompound size = r.getCompound("Size");
            int sx = size.getInt("x");
            int sy = size.getInt("y");
            int sz = size.getInt("z");
            int ax = Math.abs(sx);
            int ay = Math.abs(sy);
            int az = Math.abs(sz);
            if (ax == 0 || ay == 0 || az == 0) continue;

            cells += (long) ax * ay * az;
            if (cells > MAX_CELLS) throw new IOException("bản vẽ quá lớn cho điện thoại");

            // Kích thước âm nghĩa là vùng trải về phía âm, góc nhỏ nhất là pos + size + 1
            int mx = sx >= 0 ? pos.getInt("x") : pos.getInt("x") + sx + 1;
            int my = sy >= 0 ? pos.getInt("y") : pos.getInt("y") + sy + 1;
            int mz = sz >= 0 ? pos.getInt("z") : pos.getInt("z") + sz + 1;

            NbtList plist = r.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
            String[] palette = new String[plist.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = plist.getCompound(i).getString("Name");
            }
            int[] idx = unpack(r.getLongArray("BlockStates"), palette.length, ax * ay * az);
            regions.add(new Region(mx, my, mz, ax, ay, az, palette, idx));

            x0 = Math.min(x0, mx);
            y0 = Math.min(y0, my);
            z0 = Math.min(z0, mz);
            x1 = Math.max(x1, mx + ax - 1);
            y1 = Math.max(y1, my + ay - 1);
            z1 = Math.max(z1, mz + az - 1);

            int[] perPalette = new int[palette.length];
            for (int v : idx) {
                if (v >= 0 && v < palette.length) perPalette[v]++;
            }
            for (int i = 0; i < palette.length; i++) {
                String n = palette[i];
                if (perPalette[i] > 0 && n != null && !n.isEmpty() && !AIR.contains(n)) {
                    counts.merge(n, perPalette[i], Integer::sum);
                }
            }
        }
        if (regions.isEmpty()) throw new IOException("không có vùng nào hợp lệ");

        List<Entry> entries = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            entries.add(new Entry(e.getKey(), e.getValue()));
            total += e.getValue();
        }
        entries.sort(Comparator.comparingInt(Entry::count).reversed().thenComparing(Entry::id));

        return new Schematic(file.getFileName().toString(), regions,
                x0, y0, z0, x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1, entries, total);
    }

    /**
     * Giải nén chỉ số palette. Litematica không chèn đệm: một ô có thể nằm qua hai long,
     * nên phải đọc hai long khi cần.
     */
    private static int[] unpack(long[] data, int paletteSize, int count) throws IOException {
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
        long mask = (1L << bits) - 1;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            long start = (long) i * bits;
            int li = (int) (start >>> 6);
            int off = (int) (start & 63);
            if (li >= data.length) throw new IOException("dữ liệu khối bị thiếu");
            long v = data[li] >>> off;
            if (off + bits > 64) {
                if (li + 1 >= data.length) throw new IOException("dữ liệu khối bị thiếu");
                v |= data[li + 1] << (64 - off);
            }
            out[i] = (int) (v & mask);
        }
        return out;
    }

    public String name() { return name; }

    public int width() { return width; }

    public int height() { return height; }

    public int depth() { return depth; }

    /** Tổng số khối (không tính khí) */
    public int total() { return total; }

    /** Các loại khối, sắp theo số lượng giảm dần */
    public List<Entry> entries() { return entries; }

    /**
     * Tên khối tại tọa độ tính từ góc nhỏ nhất của bản vẽ.
     * Trả về null nếu là khí hoặc nằm ngoài bản vẽ.
     */
    public String blockAt(int rx, int ry, int rz) {
        if (rx < 0 || ry < 0 || rz < 0 || rx >= width || ry >= height || rz >= depth) return null;
        int ax = rx + minX;
        int ay = ry + minY;
        int az = rz + minZ;
        for (Region r : regions) {
            int lx = ax - r.minX();
            int ly = ay - r.minY();
            int lz = az - r.minZ();
            if (lx < 0 || ly < 0 || lz < 0 || lx >= r.sx() || ly >= r.sy() || lz >= r.sz()) continue;
            return r.at(lx, ly, lz);
        }
        return null;
    }
}
