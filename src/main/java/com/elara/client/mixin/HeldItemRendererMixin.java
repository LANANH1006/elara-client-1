package com.elara.client.mixin;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.fx.KnifeAnim;
import com.elara.client.fx.Weapon3D;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bọc quanh việc vẽ vật cầm trên tay ở góc nhìn thứ nhất.
 *
 * Chỉ khi Dao CS2 hoặc Vũ khí 3D đang bật, và đúng tay chính, HEAD mới push ma trận và bật cờ.
 * Khi tắt hết thì HEAD thoát ngay, không push và không chạm gì. RETURN pop khi cờ còn bật.
 * Nhánh vẽ vũ khí 3D tự pop rồi xóa cờ trước khi hủy, vì khi hủy ở HEAD thì RETURN không chạy.
 * Lỗi trong thân HEAD chỉ tắt mixin này, cặp push/pop vẫn cân bằng nhờ cờ.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    private static final String RENDER_FIRST_PERSON =
            "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;"
                    + "FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V";
    private static final String FAULT_KEY = "mixin.firstperson";

    /** HEAD đã push và RETURN chưa pop. Chỉ dùng trên luồng render */
    private static boolean firstPersonPushed;

    @Inject(method = RENDER_FIRST_PERSON, at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$knifeHead(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                 float swingProgress, ItemStack item, float equipProgress,
                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                 CallbackInfo ci) {
        firstPersonPushed = false;
        if (Fault.off(FAULT_KEY) || hand != Hand.MAIN_HAND) return;
        if (!ElaraConfig.isOn(Feature.WEAPON_3D) && !ElaraConfig.isOn(Feature.KNIFE_ANIM)) return;
        matrices.push();
        firstPersonPushed = true;
        try {
            if (Weapon3D.drawFirstPerson(matrices, player, item, swingProgress, equipProgress)) {
                // Đã vẽ vũ khí 3D: bỏ bản gốc của game và pop tay tự cân bằng
                firstPersonPushed = false;
                matrices.pop();
                ci.cancel();
                return;
            }
            KnifeAnim.apply(matrices, item);
        } catch (Throwable t) {
            Fault.fail(FAULT_KEY, t);
        }
    }

    @Inject(method = RENDER_FIRST_PERSON, at = @At("RETURN"), require = 0)
    private void elara$knifeReturn(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                   float swingProgress, ItemStack item, float equipProgress,
                                   MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                   CallbackInfo ci) {
        if (!firstPersonPushed) return;
        firstPersonPushed = false;
        matrices.pop();
    }
}
