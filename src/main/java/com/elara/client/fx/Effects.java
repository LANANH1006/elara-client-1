package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.core.Fault;
import com.elara.client.core.Warmup;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.function.Consumer;

/**
 * Điểm vào vẽ hiệu ứng trong thế giới. Mỗi tính năng bật thì vẽ một lượt riêng.
 *
 * Mỗi tính năng nằm trong Fault riêng: một tính năng lỗi thì tắt đúng tính năng đó và ghi một lần,
 * các tính năng còn lại vẫn vẽ. Batch được đóng bằng try-with-resources nên luôn được vẽ hoặc bỏ đi
 * trọn vẹn, không để dữ liệu dở dang.
 */
public final class Effects {

    private Effects() {}

    public static void render(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        // Đang mở túi đồ hoặc rương: tạm dừng toàn bộ hiệu ứng thế giới để đỡ tụt FPS
        if (client.currentScreen instanceof HandledScreen<?>) return;

        Vec3d cam = ctx.camera().getPos();
        Matrix4f m = ctx.matrixStack().peek().getPositionMatrix();
        float a = PlayerTracker.alpha();
        // Sau khi vào map, vài tick đầu tạm dừng các hiệu ứng nặng (xem Warmup)
        boolean heavy = Warmup.heavy();
        WorldBatch.V camV = new WorldBatch.V(cam.x, cam.y, cam.z);

        if (ElaraConfig.isOn(Feature.JUMP_RING)) {
            batch("fx.jump", m, cam, false, b -> JumpRings.draw(b, client,
                    ElaraConfig.layout(Feature.JUMP_RING), ElaraConfig.color(Feature.JUMP_RING)));
        }
        if (ElaraConfig.isOn(Feature.FOOTSTEP_AURA)) {
            batch("fx.footstep", m, cam, true, b -> Footsteps.draw(b, client,
                    ElaraConfig.layout(Feature.FOOTSTEP_AURA), ElaraConfig.color(Feature.FOOTSTEP_AURA)));
        }
        if (ElaraConfig.isOn(Feature.HEAD_ACCESSORY)) {
            batch("fx.halo", m, cam, true, b -> Halo.draw(b, client,
                    ElaraConfig.layout(Feature.HEAD_ACCESSORY), ElaraConfig.color(Feature.HEAD_ACCESSORY),
                    a, new WorldBatch.V(cam.x, cam.y, cam.z)));
        }
        if (ElaraConfig.isOn(Feature.SWORD_TRAIL)) {
            batch("fx.sword", m, cam, true, b -> SwordTrail.draw(b, client,
                    ElaraConfig.layout(Feature.SWORD_TRAIL), ElaraConfig.color(Feature.SWORD_TRAIL)));
        }
        if (ElaraConfig.isOn(Feature.PVP_REACH)) {
            batch("fx.reach", m, cam, false, b -> Reach.draw(b, client, ElaraConfig.color(Feature.PVP_REACH), a));
        }
        if (ElaraConfig.isOn(Feature.RAIN_3D)) {
            batch("fx.rain", m, cam, false, b -> Rain.draw(b, camV,
                    ElaraConfig.layout(Feature.RAIN_3D), ElaraConfig.color(Feature.RAIN_3D)));
        }
        if (ElaraConfig.isOn(Feature.HIT_EFFECT)) {
            batch("fx.hit", m, cam, true, b -> HitFx.draw(b, camV,
                    ElaraConfig.layout(Feature.HIT_EFFECT), ElaraConfig.color(Feature.HIT_EFFECT)));
        }
        if (heavy && ElaraConfig.isOn(Feature.VIRTUAL_WINGS)) {
            Fault.run("fx.wings", () -> Wings.draw(m, client, ElaraConfig.layout(Feature.VIRTUAL_WINGS),
                    ElaraConfig.color(Feature.VIRTUAL_WINGS), a, camV));
        }
        // Thế giới sống động: bầu trời, gợn sóng mưa, hạt môi trường, Mist/Glass/Bokeh
        if (heavy) Fault.run("fx.world", () -> WorldFx.draw(m, camV, client));
        // Bóng mềm dưới chân: đĩa tròn mờ, mờ dần khi nhảy
        if (heavy && SoftShadow.enabled()) Fault.run("fx.shadow", () -> SoftShadow.draw(m, camV));
        // Hiệu ứng PvP: sóng đánh trúng, tàn quang, sóng xung kích, phá khiên
        if (heavy) Fault.run("fx.combat", () -> CombatFx.draw(m, camV));
        if (ElaraConfig.isOn(Feature.CAPE)) {
            batch("fx.cape", m, cam, false, b -> Cape.draw(b, client,
                    ElaraConfig.layout(Feature.CAPE), ElaraConfig.color(Feature.CAPE), a));
        }
        if (ElaraConfig.isOn(Feature.BODY_GLOW)) {
            batch("fx.bodyglow", m, cam, true, b -> BodyGlow.draw(b, client,
                    ElaraConfig.layout(Feature.BODY_GLOW), ElaraConfig.color(Feature.BODY_GLOW), a));
        }
        if (ElaraConfig.isOn(Feature.RIBBON_TRAIL)) {
            batch("fx.ribbon", m, cam, true, b -> RibbonTrail.draw(b, client,
                    ElaraConfig.layout(Feature.RIBBON_TRAIL), ElaraConfig.color(Feature.RIBBON_TRAIL),
                    a, new WorldBatch.V(cam.x, cam.y, cam.z)));
        }
        if (ElaraConfig.isOn(Feature.BUILD_GUIDE)) {
            batch("fx.buildguide", m, cam, false, b -> BuildGuide.draw(b));
        }
        // Biến hình máy bay: chỉ khi bật và đang ở trạng thái cho phép
        if (heavy && ElaraConfig.isOn(Feature.AIRPLANE_MORPH)) {
            Fault.run("fx.airplane", () -> Airplane.draw(m, client, camV, a));
        }
        // Vũ khí 3D ở góc nhìn thứ ba: chỉ vẽ khi mixin đã đánh dấu người chơi local trong lượt này
        if (heavy) Fault.run("fx.weapon3d", () -> Weapon3D.drawThirdPersonIfMarked(cam));
    }

    /** Một lượt vẽ bằng WorldBatch. Batch luôn được đóng để vẽ hoặc bỏ đi trọn vẹn, kể cả khi lỗi. */
    private static void batch(String key, Matrix4f m, Vec3d cam, boolean additive, Consumer<WorldBatch> draw) {
        Fault.run(key, () -> {
            try (WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, additive)) {
                draw.accept(b);
            }
        });
    }
}
