package com.elara.client.mixin;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tắt hoạt ảnh khối (nước, lửa, dung nham, portal): bỏ qua bước cập nhật frame của sprite động.
 * Lớp bên trong nên target dùng tên chuỗi.
 */
@Mixin(targets = "net.minecraft.client.texture.SpriteContents$AnimatorImpl")
public abstract class SpriteAnimatorMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$noBlockAnim(CallbackInfo ci) {
        if (ElaraConfig.isOn(Feature.NO_BLOCK_ANIM)) {
            ci.cancel();
        }
    }
}
