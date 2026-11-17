package com.elara.client.mixin;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.core.Gate;
import com.elara.client.fx.Weapon3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Góc nhìn thứ ba (F5, FreeLook): khi người chơi local đang cầm kiếm, bỏ vật cầm gốc của game rồi
 * để Weapon3D vẽ dao CS2 / Katana 3D. Người chơi local được nhận biết nhờ EntityRenderDispatcherMixin,
 * nên người chơi khác đứng sát không bị thay nhầm.
 *
 * Chữ ký lấy từ mapping Yarn 1.21.4 (mappings/net/minecraft/client/render/entity/feature/HeldItemFeatureRenderer.mapping):
 * HeldItemFeatureRenderer tự khai báo renderItem(ArmedEntityRenderState, ItemRenderState, Arm, MatrixStack,
 * VertexConsumerProvider, int). Không nhắm render(...): method đó nằm ở lớp cha FeatureRenderer với kiểu
 * EntityRenderState, nên mixin sẽ lệch chữ ký và làm game lỗi lúc áp mixin.
 *
 * renderItem chạy một lần cho mỗi tay. Tay phụ luôn trống khi Weapon3D thay thế, nên hủy cả hai lần không làm
 * mất gì; việc vẽ thật nằm ở drawThirdPersonIfMarked, không nằm trong lượt này.
 * require = 0: nếu chữ ký khác thì mixin bị bỏ qua, game vẫn chạy bình thường.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {

    private static final String FAULT_KEY = "mixin.thirdperson";

    @Inject(
            method = "renderItem(Lnet/minecraft/client/render/entity/state/ArmedEntityRenderState;"
                    + "Lnet/minecraft/client/render/item/ItemRenderState;"
                    + "Lnet/minecraft/util/Arm;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;"
                    + "I)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void elara$thirdPersonWeapon(ArmedEntityRenderState entityState, ItemRenderState itemState, Arm arm,
                                         MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                         CallbackInfo ci) {
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
