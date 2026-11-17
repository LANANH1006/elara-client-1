package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.core.Gate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Không thêm particle vào danh sách vẽ nếu nó ở xa hoặc bị che.
 * Lỗi thì tắt mixin này và particle được thêm như gốc. Không dùng lambda ở đây vì hàm chạy rất nhiều lần mỗi khung.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    private static final String FAULT_KEY = "mixin.particle";

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$cullParticle(Particle particle, CallbackInfo ci) {
        if (Fault.off(FAULT_KEY)) return;
        try {
            if (!AuthorGuard.isValid()) return;
            if (Gate.loading()) return;
            // Tắt tính năng hoặc chưa vào thế giới: không làm gì, không ép kiểu
            if (!ElaraConfig.isOn(Feature.LAG_PARTICLES)) return;
            if (MinecraftClient.getInstance().world == null) return;

            ParticleAccessor p = (ParticleAccessor) particle;
            if (!CullingManager.keepParticle(p.elaraX(), p.elaraY(), p.elaraZ())) {
                ci.cancel();
                return;
            }
            // FPS thấp: bớt khoảng 70% khói, nước bắn, vụn block
            if (CullingManager.shedParticle(particle)) ci.cancel();
        } catch (Throwable t) {
            Fault.fail(FAULT_KEY, t);
        }
    }
}
