package com.elara.client.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Đọc vị trí particle. Mixin sẽ gắn interface này vào class Particle. */
@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("x")
    double elaraX();

    @Accessor("y")
    double elaraY();

    @Accessor("z")
    double elaraZ();
}
