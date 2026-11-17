package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.core.Gate;
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

    private static final String FAULT_KEY = "mixin.blockentity";

    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$cullBlockEntity(BlockEntity blockEntity, float tickDelta, MatrixStack matrices,
                                       VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (Fault.off(FAULT_KEY)) return;
        try {
            if (!AuthorGuard.isValid()) return;
            if (Gate.loading()) return;
            if (!ElaraConfig.isOn(Feature.LAG_BLOCK_ENTITIES)) return;
            // Vật ở tay (rương, biển cầm trên tay) không gắn với thế giới nên không có vị trí thật: luôn vẽ
            if (blockEntity.getWorld() == null) return;
            if (!CullingManager.keepBlockEntity(blockEntity.getPos())) ci.cancel();
        } catch (Throwable t) {
            Fault.fail(FAULT_KEY, t);
        }
    }
}
