package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.core.Fault;
import com.elara.client.core.Guard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Biến hình máy bay khi bay (bay sáng tạo hoặc cánh lượn Elytra). Mô hình người của chính mình được ẩn
 * (EntityRenderDispatcherMixin), và máy bay phản lực được vẽ bằng WorldBatch quanh người chơi, không dùng texture,
 * nên nhẹ. Không vẽ ở góc nhìn thứ nhất vì camera nằm trong thân máy bay.
 */
public final class Airplane {

    private static final int BODY = 0xFFF4F6FB;
    private static final int BODY_DARK = 0xFFC9D1DE;
    private static final int WING = 0xFFDCE3EF;
    private static final int ACCENT = 0xFFFF85B3;
    private static final int GLASS = 0xCC7FD4FF;
    private static final int EXHAUST = 0xE6FFB3D1;
    private static final int RING_SEGS = 6;

    private static boolean broken;
    /** Yaw của khung hình trước và góc nghiêng cánh hiện tại (độ), để nghiêng khi lượn */
    private static float lastYaw = Float.NaN;
    private static float bank;

    private Airplane() {}

    /** Người chơi local đang bay và tính năng bật: ẩn mô hình người gốc */
    public static boolean hidesBody(Entity entity) {
        if (broken || !ElaraConfig.isOn(Feature.AIRPLANE_MORPH)) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || entity != client.player) return false;
        return isFlying(client.player);
    }

    private static boolean isFlying(AbstractClientPlayerEntity p) {
        return p.getAbilities().flying || p.isGliding();
    }

    /** Vẽ máy bay quanh người chơi local. Lỗi thì tắt tính năng này, không làm sập game. */
    public static void draw(Matrix4f m, MinecraftClient client, WorldBatch.V cam, float alpha) {
        if (broken || !ElaraConfig.isOn(Feature.AIRPLANE_MORPH)) return;
        AbstractClientPlayerEntity p = client.player;
        if (p == null || client.options == null || client.options.getPerspective().isFirstPerson()) return;
        if (!isFlying(p)) {
            lastYaw = Float.NaN;
            bank = 0f;
            return;
        }
        try {
            Vec3d pos = p.getLerpedPos(alpha);
            float yaw0 = p.getYaw();
            // Quay phải (yaw tăng) thì cánh phải hạ xuống; độ nghiêng tối đa 35 độ, bám mượt theo khung hình
            float delta = Float.isNaN(lastYaw) ? 0f : wrap(yaw0 - lastYaw);
            lastYaw = yaw0;
            float target = Math.max(-35f, Math.min(35f, -delta * 4f));
            bank += (target - bank) * 0.12f;

            double yaw = Math.toRadians(yaw0);
            double pitch = Math.toRadians(p.getPitch());
            double cp = Math.cos(pitch);
            double sp = Math.sin(pitch);
            // Minecraft: yaw 0 nhìn về +z. Hướng phải là ngang, hướng lên tính từ phải và hướng nhìn.
            WorldBatch.V f = new WorldBatch.V(-Math.sin(yaw) * cp, -sp, Math.cos(yaw) * cp);
            WorldBatch.V r0 = new WorldBatch.V(-Math.cos(yaw), 0, -Math.sin(yaw));
            WorldBatch.V u0 = cross(r0, f);
            // Roll quanh trục nhìn: đưa hướng phải và hướng lên đã nghiêng
            double br = Math.toRadians(bank);
            double cb = Math.cos(br);
            double sb = Math.sin(br);
            WorldBatch.V r = r0.mul(cb).add(u0.mul(sb));
            WorldBatch.V u = u0.mul(cb).add(r0.mul(-sb));
            WorldBatch.V center = new WorldBatch.V(pos.x, pos.y + 0.9, pos.z);

            WorldBatch b = new WorldBatch(m, cam.x(), cam.y(), cam.z(), false);
            drawBody(b, center, f, r, u);
            b.flush();

            // Khói phản lực: cộng sáng, dài theo tốc độ
            double speed = p.getVelocity().length();
            double len = Math.min(3.0, 0.5 + speed * 8.0);
            WorldBatch glow = new WorldBatch(m, cam.x(), cam.y(), cam.z(), true);
            drawExhaust(glow, center, f, r, u, len);
            glow.flush();
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            broken = true;
            Fault.fail("fx.airplane", t);
        }
    }

    private static void drawBody(WorldBatch b, WorldBatch.V c, WorldBatch.V f, WorldBatch.V r, WorldBatch.V u) {
        // Thân: lăng trụ sáu cạnh từ đầu (a = 1.2) tới đuôi (a = -1.0), mũi nhọn tới a = 1.5
        double[] ringA = {1.2, -1.0};
        double[] ringR = {0.13, 0.10};
        WorldBatch.V[][] rings = new WorldBatch.V[2][RING_SEGS];
        for (int j = 0; j < 2; j++) {
            for (int k = 0; k < RING_SEGS; k++) {
                double th = 2 * Math.PI * k / RING_SEGS;
                rings[j][k] = point(c, f, r, u, ringA[j], Math.cos(th) * ringR[j], Math.sin(th) * ringR[j]);
            }
        }
        WorldBatch.V nose = point(c, f, r, u, 1.5, 0, 0);
        for (int k = 0; k < RING_SEGS; k++) {
            int k2 = (k + 1) % RING_SEGS;
            int col = (k % 2 == 0) ? BODY : BODY_DARK;
            b.tri(nose, rings[0][k], rings[0][k2], col);
            b.quad(rings[0][k], rings[1][k], rings[1][k2], rings[0][k2], col);
        }
        // Đuôi phẳng ở a = -1.0 (đã có cạnh nối), thêm vây đứng và hai cánh đuôi ngang
        WorldBatch.V fin0 = point(c, f, r, u, -0.45, 0, 0.12);
        WorldBatch.V fin1 = point(c, f, r, u, -1.05, 0, 0.12);
        WorldBatch.V finTop = point(c, f, r, u, -0.95, 0, 0.62);
        b.tri(fin0, fin1, finTop, ACCENT);
        for (int sign = -1; sign <= 1; sign += 2) {
            WorldBatch.V h0 = point(c, f, r, u, -0.55, sign * 0.10, 0.02);
            WorldBatch.V h1 = point(c, f, r, u, -1.0, sign * 0.52, 0.02);
            WorldBatch.V h2 = point(c, f, r, u, -1.1, sign * 0.52, 0.02);
            WorldBatch.V h3 = point(c, f, r, u, -1.0, sign * 0.10, 0.02);
            b.quad(h0, h1, h2, h3, WING);
        }
        // Hai cánh chính: gốc gắn thân
        for (int sign = -1; sign <= 1; sign += 2) {
            WorldBatch.V root0 = point(c, f, r, u, 0.45, sign * 0.12, 0.02);
            WorldBatch.V root1 = point(c, f, r, u, -0.45, sign * 0.12, 0.02);
            WorldBatch.V tip0 = point(c, f, r, u, 0.10, sign * 1.35, 0.0);
            WorldBatch.V tip1 = point(c, f, r, u, -0.55, sign * 1.35, 0.0);
            b.quad(root0, tip0, tip1, root1, WING);
        }
        // Buồng lái kính màu ở trên mũi
        WorldBatch.V g0 = point(c, f, r, u, 0.85, 0.10, 0.13);
        WorldBatch.V g1 = point(c, f, r, u, 0.85, -0.10, 0.13);
        WorldBatch.V gTop = point(c, f, r, u, 0.55, 0, 0.30);
        b.tri(g0, g1, gTop, GLASS);
    }

    /** Hai luồng khói phản lực phụt ra từ hai động cơ nằm dưới cánh, về phía sau máy bay */
    private static void drawExhaust(WorldBatch b, WorldBatch.V c, WorldBatch.V f, WorldBatch.V r, WorldBatch.V u,
                                    double len) {
        for (int sign = -1; sign <= 1; sign += 2) {
            WorldBatch.V from = point(c, f, r, u, -0.35, sign * 0.55, -0.14);
            WorldBatch.V to = point(c, f, r, u, -0.35 - len, sign * 0.55, -0.14);
            b.beam(from, to, 0.05, EXHAUST);
        }
    }

    /** Đưa độ lệch góc về khoảng (-180, 180] để không nhảy 360 độ khi yaw quay qua mốc */
    private static float wrap(float d) {
        float x = d % 360f;
        if (x > 180f) x -= 360f;
        if (x < -180f) x += 360f;
        return x;
    }

    private static WorldBatch.V point(WorldBatch.V c, WorldBatch.V f, WorldBatch.V r, WorldBatch.V u,
                                      double a, double lateral, double vertical) {
        return c.add(f.mul(a)).add(r.mul(lateral)).add(u.mul(vertical));
    }

    private static WorldBatch.V cross(WorldBatch.V a, WorldBatch.V b) {
        return new WorldBatch.V(a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x());
    }
}
