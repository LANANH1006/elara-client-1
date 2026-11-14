package com.elara.client.mixin;

import com.elara.client.fx.KnifeAnim;
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
 * Bọc quanh việc vẽ vật cầm trên tay: push ma trận, áp hoạt ảnh dao, rồi pop.
 * Push và pop luôn đi cặp với nhau nên không lệch dù tính năng bật hay tắt.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    private static final String RENDER_FIRST_PERSON =
            "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;"
                    + "FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V";

    @Inject(method = RENDER_FIRST_PERSON, at = @At("HEAD"), require = 0)
    private void elara$knifeHead(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                 float swingProgress, ItemStack item, float equipProgress,
                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                 CallbackInfo ci) {
        matrices.push();
        if (hand == Hand.MAIN_HAND) {
            KnifeAnim.apply(matrices, item);
        }
    }

    @Inject(method = RENDER_FIRST_PERSON, at = @At("RETURN"), require = 0)
    private void elara$knifeReturn(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                   float swingProgress, ItemStack item, float equipProgress,
                                   MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                   CallbackInfo ci) {
        matrices.pop();
    }
}
