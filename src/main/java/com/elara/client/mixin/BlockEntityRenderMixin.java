package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Không vẽ block entity (rương, biển, đầu người...) khi ở xa hơn khoảng cách cấu hình */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderMixin {

    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$cullBlockEntity(BlockEntity blockEntity, float tickDelta, MatrixStack matrices,
                                       VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (!AuthorGuard.isValid()) return;
        if (!CullingManager.keepBlockEntity(blockEntity.getPos())) ci.cancel();
    }
}
