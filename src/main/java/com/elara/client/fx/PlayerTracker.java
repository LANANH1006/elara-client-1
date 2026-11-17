package com.elara.client.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Theo dõi vị trí người chơi qua từng tick để nội suy mượt khi vẽ,
 * và ghi lại các lần nhảy để vẽ vòng dưới chân.
 */
public final class PlayerTracker {

    /** Vị trí hai tick gần nhất và trạng thái chạm đất */
    static final class Track {
        double px, py, pz;
        double x, y, z;
        float yaw;
        /** Góc thân (không phải góc đầu) ở tick này và tick trước, để nội suy cánh */
        float bodyYaw;
        float prevBodyYaw;
        /** Đang cúi (giữ Shift) để cánh hạ xuống */
        boolean sneaking;
        boolean onGround;
        boolean ready;
        long lastStep;
    }

    /** Một lần nhảy: người nhảy, vị trí chân, thời điểm bắt đầu */
    public record Jump(UUID owner, double x, double y, double z, long startMs) {}

    /** Một bước chân trên mặt đất */
    public record Step(UUID owner, double x, double y, double z, long startMs) {}

    /** Một nhát chém bằng kiếm của người chơi tại chỗ (chỉ người chơi đang dùng game) */
    public record Slash(UUID owner, double x, double y, double z, float yaw, long startMs) {}

    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static final List<Jump> JUMPS = new ArrayList<>();
    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<Slash> SLASHES = new ArrayList<>();
    private static long lastTickMs = System.currentTimeMillis();
    private static boolean lastAttack = false;

    private PlayerTracker() {}

    public static void tick(MinecraftClient client) {
        long now = System.currentTimeMillis();
        lastTickMs = now;
        if (client.world == null) {
            TRACKS.clear();
            JUMPS.clear();
            STEPS.clear();
            SLASHES.clear();
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (PlayerEntity p : client.world.getPlayers()) {
            UUID id = p.getUuid();
            seen.add(id);
            Track t = TRACKS.computeIfAbsent(id, k -> new Track());
            if (!t.ready) {
                t.px = t.x = p.getX();
                t.py = t.y = p.getY();
                t.pz = t.z = p.getZ();
                t.onGround = p.isOnGround();
                t.bodyYaw = t.prevBodyYaw = p.getBodyYaw();
                t.ready = true;
            } else {
                t.px = t.x;
                t.py = t.y;
                t.pz = t.z;
                t.x = p.getX();
                t.y = p.getY();
                t.z = p.getZ();
            }
            t.yaw = p.getYaw();
            t.prevBodyYaw = t.bodyYaw;
            t.bodyYaw = p.getBodyYaw();
            t.sneaking = p.isInSneakingPose();
            boolean ground = p.isOnGround();
            if (t.onGround && !ground && p.getVelocity().y > 0.1) {
                JUMPS.add(new Jump(id, t.x, t.y, t.z, now));
            }
            // Bước chân: đang đứng trên đất, di chuyển ngang, và cách lần trước đủ lâu
            double moved = Math.hypot(t.x - t.px, t.z - t.pz);
            if (ground && moved > 0.05 && now - t.lastStep > 280L) {
                STEPS.add(new Step(id, t.x, t.y, t.z, now));
                t.lastStep = now;
            }
            t.onGround = ground;
        }

        // Chém: cạnh lên của nút tấn công khi đang cầm kiếm (chỉ người chơi tại máy)
        PlayerEntity me = client.player;
        boolean attack = client.options.attackKey.isPressed();
        if (me != null && attack && !lastAttack && me.getMainHandStack().getItem() instanceof SwordItem) {
            SLASHES.add(new Slash(me.getUuid(), me.getX(), me.getY(), me.getZ(), me.getYaw(), now));
        }
        lastAttack = attack;

        TRACKS.keySet().retainAll(seen);
        JUMPS.removeIf(j -> now - j.startMs() > 2000L);
        STEPS.removeIf(s -> now - s.startMs() > 2000L);
        SLASHES.removeIf(s -> now - s.startMs() > 2000L);
    }

    /** Tỉ lệ nội suy từ tick trước đến tick hiện tại, từ 0 đến 1 */
    public static float alpha() {
        return (float) Math.min(1.0, (System.currentTimeMillis() - lastTickMs) / 50.0);
    }

    public static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    static Map<UUID, Track> tracks() {
        return TRACKS;
    }

    static List<Jump> jumps() {
        return JUMPS;
    }

    static List<Step> steps() {
        return STEPS;
    }

    static List<Slash> slashes() {
        return SLASHES;
    }
}
