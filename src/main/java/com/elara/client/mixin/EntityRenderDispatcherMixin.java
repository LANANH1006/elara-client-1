package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import com.elara.client.fx.Airplane;
import com.elara.client.fx.Weapon3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trước khi vẽ mỗi thực thể: đánh dấu có phải người chơi local không (để vũ khí 3D ở F5 nhận biết đúng người),
 * ẩn mô hình người chơi local khi đang biến hình máy bay, và không vẽ entity ở xa hoặc bị tường che.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$cullEntity(Entity entity, Frustum frustum, double x, double y, double z,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (!AuthorGuard.isValid()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Weapon3D.setRenderingLocal(client != null && entity == client.player);

        if (Airplane.hidesBody(entity)) {
            cir.setReturnValue(false);
            return;
        }
        if (!CullingManager.keepEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}
