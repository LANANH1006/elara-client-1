package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.schematic.BuilderState;
import com.elara.client.schematic.Schematic;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Khung hướng dẫn xây: tô chỗ bản vẽ đang thiếu khối (vàng) hoặc đặt sai khối (đỏ).
 * Chỉ đọc thế giới và vẽ khung. Không gửi gói tin, không đặt khối thay người chơi.
 * So sánh theo tên khối. Hướng (facing, half) và trạng thái (delay, mode) chưa được so.
 */
public final class BuildGuide {

    private static final int RADIUS = 12;
    private static final int INTERVAL = 10;
    private static final int MAX_MARKS = 400;
    private static final int MISSING_COLOR = 0x66FFD60A;
    private static final int WRONG_COLOR = 0x88FF453A;

    private record Mark(BlockPos pos, boolean missing) {}

    private static final List<Mark> marks = new ArrayList<>();
    private static int missing;
    private static int wrong;
    private static int counter;

    private BuildGuide() {}

    public static int missing() { return missing; }

    public static int wrong() { return wrong; }

    /** Gọi mỗi client tick. Quét lại mỗi 10 tick, quanh người chơi trong bán kính 12 khối. */
    public static void tick(MinecraftClient client) {
        if (!ElaraConfig.isOn(Feature.BUILD_GUIDE)) {
            if (!marks.isEmpty() || missing != 0 || wrong != 0) {
                marks.clear();
                missing = 0;
                wrong = 0;
            }
            return;
        }
        if (++counter < INTERVAL) return;
        counter = 0;
        marks.clear();
        missing = 0;
        wrong = 0;
        if (client.world == null || client.player == null) return;

        Schematic s = BuilderState.loaded();
        BlockPos o = BuilderState.origin();
        if (s == null || o == null) return;

        BlockPos p = client.player.getBlockPos();
        int x0 = Math.max(p.getX() - RADIUS, o.getX());
        int x1 = Math.min(p.getX() + RADIUS, o.getX() + s.width() - 1);
        int y0 = Math.max(p.getY() - RADIUS, o.getY());
        int y1 = Math.min(p.getY() + RADIUS, o.getY() + s.height() - 1);
        int z0 = Math.max(p.getZ() - RADIUS, o.getZ());
        int z1 = Math.min(p.getZ() + RADIUS, o.getZ() + s.depth() - 1);

        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    String want = s.blockAt(x - o.getX(), y - o.getY(), z - o.getZ());
                    if (want == null) continue;
                    want = ElaraConfig.remap(want);

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = client.world.getBlockState(pos);
                    boolean isMissing = state.isAir();
                    boolean isWrong = !isMissing
                            && !Registries.BLOCK.getId(state.getBlock()).toString().equals(want);
                    if (!isMissing && !isWrong) continue;

                    if (isMissing) missing++;
                    else wrong++;
                    if (marks.size() < MAX_MARKS) marks.add(new Mark(pos, isMissing));
                }
            }
        }
    }

    /** Vẽ các khung đã tính. Được gọi trong lượt vẽ thế giới. */
    public static void draw(WorldBatch b) {
        for (Mark m : marks) {
            BlockPos p = m.pos();
            box(b, p.getX(), p.getY(), p.getZ(), m.missing() ? MISSING_COLOR : WRONG_COLOR);
        }
    }

    /** Khối hộp 1x1x1 bằng sáu mặt, hơi phồng ra để không lẫn với mặt của khối thật */
    private static void box(WorldBatch b, int x, int y, int z, int color) {
        double x0 = x - 0.005;
        double x1 = x + 1.005;
        double y0 = y - 0.005;
        double y1 = y + 1.005;
        double z0 = z - 0.005;
        double z1 = z + 1.005;
        WorldBatch.V p000 = new WorldBatch.V(x0, y0, z0);
        WorldBatch.V p100 = new WorldBatch.V(x1, y0, z0);
        WorldBatch.V p010 = new WorldBatch.V(x0, y1, z0);
        WorldBatch.V p110 = new WorldBatch.V(x1, y1, z0);
        WorldBatch.V p001 = new WorldBatch.V(x0, y0, z1);
        WorldBatch.V p101 = new WorldBatch.V(x1, y0, z1);
        WorldBatch.V p011 = new WorldBatch.V(x0, y1, z1);
        WorldBatch.V p111 = new WorldBatch.V(x1, y1, z1);
        b.quad(p000, p100, p110, p010, color);
        b.quad(p001, p101, p111, p011, color);
        b.quad(p000, p001, p011, p010, color);
        b.quad(p100, p101, p111, p110, color);
        b.quad(p000, p100, p101, p001, color);
        b.quad(p010, p110, p111, p011, color);
    }
}
