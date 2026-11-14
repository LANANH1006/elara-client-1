package com.elara.client.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trạng thái chuyển động của từng người chơi, cập nhật mỗi tick: đang đi, nhảy, rơi,
 * đứng yên lâu, giữ Shift, mặc Elytra. Cánh, áo choàng và dải lụa đều đọc từ đây.
 * Cũng giữ vết vị trí gần đây để vẽ dải lụa phía sau.
 */
public final class PlayerMotion {
    /** 15 giây đứng yên thì thu cánh */
    public static final int IDLE_TICKS = 300;
    /** 5 giây giữ Shift thì thu cánh */
    public static final int SNEAK_TICKS = 100;
    /** Số điểm vết di chuyển giữ lại, mỗi tick một điểm */
    public static final int TRAIL_LEN = 24;

    public static final class State {
        /** 0 thu gọn, 1 xòe hết cỡ. Mở nhanh khi bước đi, khép chậm khi đứng yên. */
        public float spread = 0.6f;
        /** Biên độ vỗ hiện tại, 0 là không vỗ */
        public float flap = 0f;
        /** 0 là bình thường, 1 là đã thu hết vì đứng yên hoặc giữ Shift */
        public float fold = 0f;
        /** Đang mặc Elytra thì cánh ẩn */
        public boolean hidden;
        public boolean moving;
        public boolean airborne;
        public double vy;
        /** Vị trí chân gần nhất ở đầu deque, cũ nhất ở cuối */
        public final Deque<double[]> trail = new ArrayDeque<>();
        int idle;
        int sneak;
        double lastX;
        double lastZ;
        boolean init;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private PlayerMotion() {}

    public static void tick(MinecraftClient client) {
        if (client.world == null) {
            STATES.clear();
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (PlayerEntity p : client.world.getPlayers()) {
            UUID id = p.getUuid();
            seen.add(id);
            State s = STATES.computeIfAbsent(id, k -> new State());
            if (!s.init) {
                s.init = true;
                s.lastX = p.getX();
                s.lastZ = p.getZ();
            }
            double dx = p.getX() - s.lastX;
            double dz = p.getZ() - s.lastZ;
            s.lastX = p.getX();
            s.lastZ = p.getZ();

            s.moving = Math.hypot(dx, dz) > 0.02;
            s.idle = s.moving ? 0 : s.idle + 1;
            s.sneak = p.isSneaking() ? s.sneak + 1 : 0;
            s.hidden = p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
            s.airborne = !p.isOnGround();
            s.vy = p.getVelocity().y;

            boolean resting = !s.airborne && !s.moving && (s.idle >= IDLE_TICKS || s.sneak >= SNEAK_TICKS);
            float spreadTarget;
            float flapTarget;
            if (resting) {
                spreadTarget = 0.2f;
                flapTarget = 0.03f;
            } else if (s.airborne) {
                // nhảy lên hoặc rơi: xòe rộng đón gió, rơi nhanh thì rung mạnh hơn
                spreadTarget = 1f;
                flapTarget = s.vy < -0.3 ? 0.35f : 0.12f;
            } else if (s.moving) {
                spreadTarget = 0.8f;
                flapTarget = 0.6f;
            } else {
                spreadTarget = 0.6f;
                flapTarget = 0.08f;
            }
            float spreadRate = spreadTarget > s.spread ? 0.35f : 0.12f;
            s.spread += (spreadTarget - s.spread) * spreadRate;
            s.flap += (flapTarget - s.flap) * 0.2f;
            s.fold += ((resting ? 1f : 0f) - s.fold) * 0.1f;

            s.trail.addFirst(new double[] {p.getX(), p.getY(), p.getZ()});
            while (s.trail.size() > TRAIL_LEN) s.trail.removeLast();
        }
        STATES.keySet().retainAll(seen);
    }

    /** Trạng thái của một người chơi, hoặc null nếu chưa có dữ liệu */
    public static State get(UUID id) {
        return STATES.get(id);
    }
}
