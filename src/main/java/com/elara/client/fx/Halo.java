package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.UUID;

/** Phụ kiện đầu: vòng hào quang lơ lửng trên đầu, xoay chậm và có lớp sáng mờ. */
public final class Halo {

    private Halo() {}

    public static void draw(WorldBatch b, MinecraftClient client, ElaraConfig.Layout l, int argb, float a) {
        double size = Math.max(0.5, l.scalePct / 100.0);
        double rot = System.currentTimeMillis() / 1000.0 * 1.2;
        for (Map.Entry<UUID, PlayerTracker.Track> e : PlayerTracker.tracks().entrySet()) {
            PlayerTracker.Track t = e.getValue();
            double px = PlayerTracker.lerp(t.px, t.x, a);
            double py = PlayerTracker.lerp(t.py, t.y, a);
            double pz = PlayerTracker.lerp(t.pz, t.z, a);
            if (!Visibility.allows(l, client, e.getKey(), px, py, pz)) continue;

            WorldBatch.V c = new WorldBatch.V(px, py + 2.15, pz);
            b.ring(c, 0.28 * size, 0.035, 40, rot, argb);
            b.ring(c, 0.28 * size, 0.12, 40, rot, WorldBatch.withAlpha(argb, 0.25f));
        }
    }
}
