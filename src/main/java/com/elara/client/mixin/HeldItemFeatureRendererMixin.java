package com.elara.client.mixin;

import com.elara.client.fx.Weapon3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Góc nhìn thứ ba: khi người chơi local đang cầm kiếm, bỏ vật cầm gốc của game rồi
 * để Weapon3D vẽ dao CS2 / Katana 3D. Chỉ tác động tới người chơi local, không ảnh hưởng người khác.
 * require = 0: nếu chữ ký phương thức khác với dự kiến thì mixin bị bỏ qua, game vẫn chạy bình thường.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;"
                    + "ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void elara$thirdPersonWeapon(MatrixStack matrices, CallbackInfo ci) {
        if (Weapon3D.replacesThirdPerson(MinecraftClient.getInstance(), matrices)) {
            Weapon3D.markThirdPerson();
            ci.cancel();
        }
    }
}
