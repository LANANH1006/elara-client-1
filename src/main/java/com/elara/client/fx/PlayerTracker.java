package com.elara.client.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

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
        boolean onGround;
        boolean ready;
    }

    /** Một lần nhảy: người nhảy, vị trí chân, thời điểm bắt đầu */
    public record Jump(UUID owner, double x, double y, double z, long startMs) {}

    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static final List<Jump> JUMPS = new ArrayList<>();
    private static long lastTickMs = System.currentTimeMillis();

    private PlayerTracker() {}

    public static void tick(MinecraftClient client) {
        long now = System.currentTimeMillis();
        lastTickMs = now;
        if (client.world == null) {
            TRACKS.clear();
            JUMPS.clear();
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
            boolean ground = p.isOnGround();
            if (t.onGround && !ground && p.getVelocity().y > 0.1) {
                JUMPS.add(new Jump(id, t.x, t.y, t.z, now));
            }
            t.onGround = ground;
        }
        TRACKS.keySet().retainAll(seen);
        JUMPS.removeIf(j -> now - j.startMs() > 2000L);
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
}
