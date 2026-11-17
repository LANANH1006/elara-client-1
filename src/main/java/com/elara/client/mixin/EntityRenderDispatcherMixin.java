package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.core.Gate;
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
 * Khi ba tính năng dùng đến thực thể đều tắt thì thoát ngay, không làm gì thêm.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    private static final String FAULT_KEY = "mixin.entitycull";

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$cullEntity(Entity entity, Frustum frustum, double x, double y, double z,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (Fault.off(FAULT_KEY)) return;
        try {
            if (!AuthorGuard.isValid()) return;
            if (Gate.loading()) return;
            if (!ElaraConfig.isOn(Feature.LAG_ENTITIES) && !ElaraConfig.isOn(Feature.AIRPLANE_MORPH)
                    && !ElaraConfig.isOn(Feature.WEAPON_3D)) {
                return;
            }
            // Chưa vào thế giới thì không có thực thể để lọc: thoát sớm
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) {
                Weapon3D.setRenderingLocal(false);
                return;
            }
            Weapon3D.setRenderingLocal(entity == client.player);

            if (Airplane.hidesBody(entity)) {
                cir.setReturnValue(false);
                return;
            }
            if (!CullingManager.keepEntity(entity)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable t) {
            Fault.fail(FAULT_KEY, t);
        }
    }
}
