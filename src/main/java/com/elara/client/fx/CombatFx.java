package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hiệu ứng PvP trong thế giới: Sóng đánh trúng, Tàn quang khi hạ gục, Sóng xung kích mặt đất,
 * Phá khiên (hạt vỡ và thời gian hồi 4.5 giây). Trạng thái tick trên client, vẽ qua Effects.
 * Target HUD đọc thời gian hồi khiên qua shieldRemainingMs.
 */
public final class CombatFx {
    private static final long RIPPLE_MS = 420L;
    private static final long GLOW_MS = 1500L;
    private static final long SHOCK_MS = 520L;
    private static final long SHARD_MS = 700L;
    private static final long HIT_MEMORY_MS = 3000L;
    /** Thời gian hồi khiên sau khi bị rìu phá */
    public static final long SHIELD_MS = 4500L;
    private static final int MAX_EFFECTS = 40;
    private static final WorldBatch.V UP = new WorldBatch.V(0, 1, 0);

    private record Ripple(double x, double y, double z, long start) {}

    private record Glow(double x, double y, double z, double h, long start) {}

    private record Shock(double x, double y, double z, long start) {}

    private record Shard(double x, double y, double z, double vx, double vy, double vz, long start) {}

    private record Hit(LivingEntity entity, long at) {}

    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static final List<Glow> GLOWS = new ArrayList<>();
    private static final List<Shock> SHOCKS = new ArrayList<>();
    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final Map<UUID, Hit> RECENT = new HashMap<>();
    private static final Map<UUID, Long> SHIELD_BROKEN = new HashMap<>();
    /** TNT và tinh thể End đang thấy gần người chơi, lần tick trước: id -> x, y, z */
    private static final Map<UUID, double[]> BOMBS = new HashMap<>();

    private static float lastHealth = -1f;
    private static boolean wasOnGround = true;
    private static double peakY = 0;
    private static boolean broken = false;

    private CombatFx() {}

    /** Có tính năng nào trong nhóm này đang bật không */
    public static boolean active() {
        return !broken && (ElaraConfig.isOn(Feature.HIT_RIPPLE) || ElaraConfig.isOn(Feature.AFTERGLOW)
                || ElaraConfig.isOn(Feature.SHOCKWAVE) || ElaraConfig.isOn(Feature.SHIELD_BREAKER));
    }

    /** Mili giây còn lại của hồi khiên sau khi bị phá, 0 nếu khiên đã sẵn sàng */
    public static long shieldRemainingMs(UUID id) {
        Long at = SHIELD_BROKEN.get(id);
        if (at == null) return 0L;
        long left = SHIELD_MS - (System.currentTimeMillis() - at);
        if (left <= 0) {
            SHIELD_BROKEN.remove(id);
            return 0L;
        }
        return left;
    }

    /** Gọi mỗi tick trên client */
    public static void tick(MinecraftClient client) {
        // Tắt hết hiệu ứng PvP thì xóa các hạt đang còn và thôi tính
        if (!active()) {
            clear();
            return;
        }
        long now = System.currentTimeMillis();
        PlayerEntity me = client.player;
        if (client.world == null || me == null) {
            clear();
            return;
        }

        // Mình bị đánh: máu giảm thì sóng nổ quanh mình
        float hp = me.getHealth();
        if (lastHealth >= 0 && hp < lastHealth - 0.01f && ElaraConfig.isOn(Feature.HIT_RIPPLE)) {
            addRipple(me, now);
        }
        lastHealth = hp;

        // Chạm đất sau cú rơi từ 3 block trở lên: sóng xung kích dưới chân
        boolean ground = me.isOnGround();
        if (ground) {
            if (!wasOnGround && peakY - me.getY() >= 3.0 && ElaraConfig.isOn(Feature.SHOCKWAVE)) {
                SHOCKS.add(new Shock(me.getX(), me.getY(), me.getZ(), now));
            }
            peakY = me.getY();
        } else {
            peakY = Math.max(peakY, me.getY());
        }
        wasOnGround = ground;

        // Vụ nổ gần mình (TNT, tinh thể End): sóng xung kích tại chỗ nổ
        trackExplosions(client, me, now);

        // Mục tiêu vừa bị mình đánh, giờ hết máu: tàn quang
        Iterator<Map.Entry<UUID, Hit>> it = RECENT.entrySet().iterator();
        while (it.hasNext()) {
            Hit h = it.next().getValue();
            if (now - h.at() > HIT_MEMORY_MS) {
                it.remove();
                continue;
            }
            LivingEntity e = h.entity();
            if (e.getHealth() <= 0f) {
                if (ElaraConfig.isOn(Feature.AFTERGLOW)) {
                    GLOWS.add(new Glow(e.getX(), e.getY(), e.getZ(), e.getHeight(), now));
                }
                it.remove();
            }
        }

        RIPPLES.removeIf(r -> now - r.start() > RIPPLE_MS);
        GLOWS.removeIf(g -> now - g.start() > GLOW_MS);
        SHOCKS.removeIf(s -> now - s.start() > SHOCK_MS);
        SHARDS.removeIf(s -> now - s.start() > SHARD_MS);
        trim(RIPPLES);
        trim(GLOWS);
        trim(SHOCKS);
    }

    /**
     * TNT và tinh thể End nổ rồi biến mất khỏi thế giới. Nếu chúng biến mất trong bán kính 12 block
     * quanh người chơi thì coi như có một vụ nổ ở vị trí lần cuối thấy chúng.
     */
    private static void trackExplosions(MinecraftClient client, PlayerEntity me, long now) {
        if (!ElaraConfig.isOn(Feature.SHOCKWAVE)) {
            BOMBS.clear();
            return;
        }
        Box box = me.getBoundingBox().expand(14.0);
        Map<UUID, double[]> seen = new HashMap<>();
        for (Entity e : client.world.getEntitiesByClass(TntEntity.class, box, x -> true)) {
            seen.put(e.getUuid(), new double[] {e.getX(), e.getY(), e.getZ()});
        }
        for (Entity e : client.world.getEntitiesByClass(EndCrystalEntity.class, box, x -> true)) {
            seen.put(e.getUuid(), new double[] {e.getX(), e.getY(), e.getZ()});
        }
        for (Map.Entry<UUID, double[]> gone : BOMBS.entrySet()) {
            if (seen.containsKey(gone.getKey())) continue;
            double[] p = gone.getValue();
            double dx = p[0] - me.getX();
            double dy = p[1] - me.getY();
            double dz = p[2] - me.getZ();
            if (dx * dx + dy * dy + dz * dz <= 12.0 * 12.0) {
                SHOCKS.add(new Shock(p[0], p[1], p[2], now));
            }
        }
        BOMBS.clear();
        BOMBS.putAll(seen);
    }

    /** Gọi khi người chơi local đánh trúng một thực thể */
    public static void onAttack(PlayerEntity player, Entity target) {
        if (!(target instanceof LivingEntity living)) return;
        long now = System.currentTimeMillis();
        RECENT.put(living.getUuid(), new Hit(living, now));
        if (ElaraConfig.isOn(Feature.HIT_RIPPLE)) addRipple(living, now);

        ItemStack held = player.getMainHandStack();
        boolean falling = !player.isOnGround() && player.getVelocity().y < -0.05;
        if (ElaraConfig.isOn(Feature.SHOCKWAVE) && (held.isOf(Items.MACE) || falling)) {
            SHOCKS.add(new Shock(living.getX(), living.getY(), living.getZ(), now));
        }

        // Rìu đánh vào người đang giơ khiên: khiên bị phá, bắt đầu đếm ngược hồi
        if (ElaraConfig.isOn(Feature.SHIELD_BREAKER) && living instanceof PlayerEntity victim
                && held.getItem() instanceof AxeItem && victim.isBlocking()) {
            SHIELD_BROKEN.put(victim.getUuid(), now);
            spawnShards(victim, now);
        }
    }

    private static void addRipple(Entity e, long now) {
        RIPPLES.add(new Ripple(e.getX(), e.getY() + e.getHeight() * 0.55, e.getZ(), now));
    }

    private static void spawnShards(Entity e, long now) {
        double cy = e.getY() + e.getHeight() * 0.6;
        for (int i = 0; i < 14; i++) {
            double ang = Math.random() * Math.PI * 2;
            double speed = 0.6 + Math.random() * 0.8;
            SHARDS.add(new Shard(e.getX(), cy, e.getZ(),
                    Math.cos(ang) * speed, 0.9 + Math.random() * 0.9, Math.sin(ang) * speed, now));
        }
    }

    private static void trim(List<?> list) {
        while (list.size() > MAX_EFFECTS) list.remove(0);
    }

    private static void clear() {
        RIPPLES.clear();
        GLOWS.clear();
        SHOCKS.clear();
        SHARDS.clear();
        RECENT.clear();
        SHIELD_BROKEN.clear();
        lastHealth = -1f;
    }

    // ---------- vẽ ----------

    /** Vẽ các hiệu ứng PvP còn sống. Tự flush batch. */
    public static void draw(Matrix4f mat, WorldBatch.V cam) {
        if (!active()) return;
        try {
            long now = System.currentTimeMillis();
            WorldBatch add = new WorldBatch(mat, cam.x(), cam.y(), cam.z(), true);
            if (ElaraConfig.isOn(Feature.HIT_RIPPLE)) drawRipples(add, now);
            if (ElaraConfig.isOn(Feature.AFTERGLOW)) drawGlows(add, cam, now);
            if (ElaraConfig.isOn(Feature.SHOCKWAVE)) drawShocks(add, now);
            if (ElaraConfig.isOn(Feature.SHIELD_BREAKER)) drawShards(add, cam, now);
            add.flush();
        } catch (RuntimeException ex) {
            broken = true;
            Fault.fail("fx.pvp", ex);
        }
    }

    /** Sóng sáng lan ra trên thân mục tiêu: vòng ngoài lan rộng, vòng trong đi chậm hơn */
    private static void drawRipples(WorldBatch b, long now) {
        int argb = ElaraConfig.color(Feature.HIT_RIPPLE);
        double k = ElaraConfig.layout(Feature.HIT_RIPPLE).get("radius", 10) / 10.0;
        for (Ripple r : RIPPLES) {
            double t = (now - r.start()) / (double) RIPPLE_MS;
            if (t < 0 || t >= 1) continue;
            double alpha = Math.pow(1 - t, 1.5);
            WorldBatch.V c = new WorldBatch.V(r.x(), r.y(), r.z());
            b.ring(c, (0.25 + 1.2 * t) * k, 0.07 + 0.05 * (1 - t), 40, 0,
                    WorldBatch.withAlpha(argb, (float) alpha));
            b.ring(c, (0.15 + 0.7 * t) * k, 0.04, 32, Math.PI / 32,
                    WorldBatch.withAlpha(argb, (float) (alpha * 0.5)));
        }
    }

    /** Thân mục tiêu tan thành hạt xoắn ốc bay lên, kèm cột tia sáng mờ dần */
    private static void drawGlows(WorldBatch b, WorldBatch.V cam, long now) {
        int argb = ElaraConfig.color(Feature.AFTERGLOW);
        int n = Math.max(20, Math.min(150, ElaraConfig.layout(Feature.AFTERGLOW).get("count", 60)));
        for (Glow g : GLOWS) {
            double t = (now - g.start()) / (double) GLOW_MS;
            if (t < 0 || t >= 1) continue;
            double a = 1 - t;
            WorldBatch.V base = new WorldBatch.V(g.x(), g.y(), g.z());
            WorldBatch.V top = base.add(UP.mul(g.h() * (2.0 + t)));
            b.billboardBeam(base, top, cam, 0.25 + 0.35 * a, WorldBatch.withAlpha(argb, (float) (0.30 * a)));
            for (int i = 0; i < n; i++) {
                double f = i / (double) n;
                double ang = i * 2.399963 + t * 4.0;
                double rise = g.h() * (0.1 + 1.6 * t * (0.4 + 0.6 * f));
                double rad = 0.6 * a * (0.4 + 0.6 * f) + 0.1;
                WorldBatch.V p = new WorldBatch.V(g.x() + Math.cos(ang) * rad, g.y() + rise,
                        g.z() + Math.sin(ang) * rad);
                double s = 0.05 * a + 0.01;
                billboardQuad(b, p, cam, s, WorldBatch.withAlpha(argb, (float) (a * (0.5 + 0.5 * f))));
            }
        }
    }

    /** Vòng sáng nở nhanh ban đầu rồi chậm lại, nằm sát mặt đất */
    private static void drawShocks(WorldBatch b, long now) {
        int argb = ElaraConfig.color(Feature.SHOCKWAVE);
        double k = ElaraConfig.layout(Feature.SHOCKWAVE).get("radius", 25) / 10.0;
        for (Shock s : SHOCKS) {
            double t = (now - s.start()) / (double) SHOCK_MS;
            if (t < 0 || t >= 1) continue;
            double a = Math.pow(1 - t, 1.4);
            double rad = (0.4 + 2.4 * Math.sqrt(t)) * k;
            WorldBatch.V c = new WorldBatch.V(s.x(), s.y() + 0.04, s.z());
            b.ring(c, rad, 0.10 * (1 - t) + 0.03, 56, 0, WorldBatch.withAlpha(argb, (float) a));
            // Quầng mờ bên trong: vòng dày bằng bán kính, nên thành một đĩa mờ
            b.ring(c, rad * 0.5, rad, 40, 0, WorldBatch.withAlpha(argb, (float) (a * 0.18)));
        }
    }

    /** Mảnh khiên vỡ bay ra rồi rơi xuống */
    private static void drawShards(WorldBatch b, WorldBatch.V cam, long now) {
        int argb = ElaraConfig.color(Feature.SHIELD_BREAKER);
        for (Shard s : SHARDS) {
            double t = (now - s.start()) / (double) SHARD_MS;
            if (t < 0 || t >= 1) continue;
            double age = (now - s.start()) / 1000.0;
            WorldBatch.V p = new WorldBatch.V(s.x() + s.vx() * age,
                    s.y() + s.vy() * age - 4.9 * age * age,
                    s.z() + s.vz() * age);
            billboardQuad(b, p, cam, 0.05 * (1 - t) + 0.015, WorldBatch.withAlpha(argb, (float) (1 - t)));
        }
    }

    /** Một quad vuông luôn quay mặt về camera */
    private static void billboardQuad(WorldBatch b, WorldBatch.V p, WorldBatch.V cam, double s, int col) {
        WorldBatch.V toCam = normalize(cam.sub(p));
        WorldBatch.V right = normalize(cross(UP, toCam));
        WorldBatch.V up = cross(toCam, right);
        b.quad(p.add(right.mul(-s)).add(up.mul(-s)), p.add(right.mul(s)).add(up.mul(-s)),
                p.add(right.mul(s)).add(up.mul(s)), p.add(right.mul(-s)).add(up.mul(s)), col);
    }

    private static WorldBatch.V cross(WorldBatch.V a, WorldBatch.V b) {
        return new WorldBatch.V(a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x());
    }

    /** Chuẩn hóa vector. Vector bằng 0 thì trả về trục X để không chia cho 0. */
    private static WorldBatch.V normalize(WorldBatch.V v) {
        double len = Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
        if (len < 1e-9) return new WorldBatch.V(1, 0, 0);
        return v.mul(1 / len);
    }
}
