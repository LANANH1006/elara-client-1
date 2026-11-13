package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Không vẽ entity ở xa hoặc bị tường che */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$cullEntity(Entity entity, Frustum frustum, double x, double y, double z,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (!AuthorGuard.isValid()) return;

        if (!CullingManager.keepEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}
