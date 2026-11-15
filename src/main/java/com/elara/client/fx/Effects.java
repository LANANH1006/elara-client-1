package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Điểm vào vẽ hiệu ứng trong thế giới. Mỗi tính năng bật thì vẽ một lượt riêng.
 * Mỗi lớp nằm trong try/catch riêng: một lớp lỗi trên GL của điện thoại thì tắt lớp đó và báo một lần,
 * các lớp khác và game vẫn chạy.
 */
public final class Effects {

    /** Hiệu ứng đã lỗi một lần thì tắt tới khi khởi động lại, để không báo lỗi mỗi khung hình */
    private static final java.util.Set<Feature> BROKEN = java.util.EnumSet.noneOf(Feature.class);
    private static boolean wingsBroken;
    private static boolean combatBroken;
    private static boolean shadowBroken;

    private Effects() {}

    public static void render(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Vec3d cam = ctx.camera().getPos();
        Matrix4f m = ctx.matrixStack().peek().getPositionMatrix();
        float a = PlayerTracker.alpha();

        if (ElaraConfig.isOn(Feature.JUMP_RING)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, false);
            JumpRings.draw(b, client, ElaraConfig.layout(Feature.JUMP_RING), ElaraConfig.color(Feature.JUMP_RING));
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.FOOTSTEP_AURA)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            Footsteps.draw(b, client, ElaraConfig.layout(Feature.FOOTSTEP_AURA),
                    ElaraConfig.color(Feature.FOOTSTEP_AURA));
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.HEAD_ACCESSORY)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            Halo.draw(b, client, ElaraConfig.layout(Feature.HEAD_ACCESSORY),
                    ElaraConfig.color(Feature.HEAD_ACCESSORY), a, new WorldBatch.V(cam.x, cam.y, cam.z));
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.SWORD_TRAIL)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            SwordTrail.draw(b, client, ElaraConfig.layout(Feature.SWORD_TRAIL),
                    ElaraConfig.color(Feature.SWORD_TRAIL));
            b.flush();
        }
        WorldBatch.V camV = new WorldBatch.V(cam.x, cam.y, cam.z);
        if (ElaraConfig.isOn(Feature.PVP_REACH)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, false);
            Reach.draw(b, client, ElaraConfig.color(Feature.PVP_REACH), a);
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.RAIN_3D)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, false);
            Rain.draw(b, camV, ElaraConfig.layout(Feature.RAIN_3D), ElaraConfig.color(Feature.RAIN_3D));
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.HIT_EFFECT)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            HitFx.draw(b, camV, ElaraConfig.layout(Feature.HIT_EFFECT), ElaraConfig.color(Feature.HIT_EFFECT));
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.VIRTUAL_WINGS) && !wingsBroken) {
            // Cánh tự tạo batch riêng: WorldBatch cho khung dây và khói, TexBatch cho lông vũ có texture
            try {
                Wings.draw(m, client, ElaraConfig.layout(Feature.VIRTUAL_WINGS),
                        ElaraConfig.color(Feature.VIRTUAL_WINGS), a, camV);
            } catch (Throwable ex) {
                wingsBroken = true;
                System.err.println("[Elara] Tắt Cánh do lỗi: " + ex);
            }
        }
        // Thế giới sống động: bầu trời, gợn sóng mưa, hạt môi trường, Mist/Glass/Bokeh (có try/catch riêng)
        WorldFx.draw(m, camV, client);
        // Bóng mềm dưới chân: đĩa tròn mờ, mờ dần khi nhảy
        if (ElaraConfig.isOn(Feature.SOFT_SHADOW) && !shadowBroken) {
            try {
                SoftShadow.draw(m, camV);
            } catch (Throwable ex) {
                shadowBroken = true;
                System.err.println("[Elara] Tắt Bóng mềm do lỗi: " + ex);
            }
        }
        // Hiệu ứng PvP: sóng đánh trúng, tàn quang, sóng xung kích, phá khiên (tự tạo batch riêng)
        if (!combatBroken) {
            try {
                CombatFx.draw(m, camV);
            } catch (Throwable ex) {
                combatBroken = true;
                System.err.println("[Elara] Tắt Hiệu ứng PvP do lỗi: " + ex);
            }
        }
        if (ElaraConfig.isOn(Feature.CAPE) && !BROKEN.contains(Feature.CAPE)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, false);
            try {
                Cape.draw(b, client, ElaraConfig.layout(Feature.CAPE), ElaraConfig.color(Feature.CAPE), a);
            } catch (Throwable ex) {
                // Lỗi trong một hiệu ứng không được làm sập game. Tắt riêng hiệu ứng này và báo một lần.
                System.err.println("[Elara] Tắt Áo choàng do lỗi: " + ex);
                BROKEN.add(Feature.CAPE);
            }
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.BODY_GLOW) && !BROKEN.contains(Feature.BODY_GLOW)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            try {
                BodyGlow.draw(b, client, ElaraConfig.layout(Feature.BODY_GLOW),
                        ElaraConfig.color(Feature.BODY_GLOW), a);
            } catch (Throwable ex) {
                System.err.println("[Elara] Tắt Viền sáng thân do lỗi: " + ex);
                BROKEN.add(Feature.BODY_GLOW);
            }
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.RIBBON_TRAIL) && !BROKEN.contains(Feature.RIBBON_TRAIL)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, true);
            try {
                RibbonTrail.draw(b, client, ElaraConfig.layout(Feature.RIBBON_TRAIL),
                        ElaraConfig.color(Feature.RIBBON_TRAIL), a, new WorldBatch.V(cam.x, cam.y, cam.z));
            } catch (Throwable ex) {
                System.err.println("[Elara] Tắt Dải lụa sau lưng do lỗi: " + ex);
                BROKEN.add(Feature.RIBBON_TRAIL);
            }
            b.flush();
        }
        if (ElaraConfig.isOn(Feature.BUILD_GUIDE)) {
            WorldBatch b = new WorldBatch(m, cam.x, cam.y, cam.z, false);
            BuildGuide.draw(b);
            b.flush();
        }
        // Vũ khí 3D ở góc nhìn thứ ba: chỉ vẽ khi mixin đã đánh dấu người chơi local trong lượt này
        Weapon3D.drawThirdPersonIfMarked(cam);
    }
}
