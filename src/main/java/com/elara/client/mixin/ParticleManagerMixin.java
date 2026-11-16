package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Không thêm particle vào danh sách vẽ nếu nó ở xa hoặc bị che */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$cullParticle(Particle particle, CallbackInfo ci) {
        if (!AuthorGuard.isValid()) return;

        ParticleAccessor p = (ParticleAccessor) particle;
        if (!CullingManager.keepParticle(p.elaraX(), p.elaraY(), p.elaraZ())) {
            ci.cancel();
            return;
        }
        // FPS thấp: bớt khoảng 70% khói, nước bắn, vụn block
        if (CullingManager.shedParticle(particle)) ci.cancel();
    }
}
