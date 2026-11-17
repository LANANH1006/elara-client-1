package com.elara.client.mixin;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.core.Gate;
import com.elara.client.fx.Weapon3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Góc nhìn thứ ba (F5, FreeLook): khi người chơi local đang cầm kiếm, bỏ vật cầm gốc của game rồi
 * để Weapon3D vẽ dao CS2 / Katana 3D. Người chơi local được nhận biết nhờ EntityRenderDispatcherMixin,
 * nên người chơi khác đứng sát không bị thay nhầm.
 *
 * Chữ ký dùng ArmedEntityRenderState (kiểu xóa của S trong HeldItemFeatureRenderer 1.21.4).
 * require = 0: nếu chữ ký khác thì mixin bị bỏ qua, game vẫn chạy bình thường.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {

    private static final String FAULT_KEY = "mixin.thirdperson";

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;"
                    + "ILnet/minecraft/client/render/entity/state/ArmedEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void elara$thirdPersonWeapon(MatrixStack matrices, CallbackInfo ci) {
        if (Fault.off(FAULT_KEY)) return;
        try {
            if (Gate.loading()) return;
            if (!ElaraConfig.isOn(Feature.WEAPON_3D)) return;
            if (!Weapon3D.isRenderingLocalPlayer()) return;
            if (Weapon3D.replacesThirdPerson(MinecraftClient.getInstance(), matrices)) {
                Weapon3D.markThirdPerson();
                ci.cancel();
            }
        } catch (Throwable t) {
            Fault.fail(FAULT_KEY, t);
        }
    }
}
