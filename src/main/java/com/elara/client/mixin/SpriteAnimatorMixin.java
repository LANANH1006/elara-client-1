package com.elara.client.mixin;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.core.Gate;
import net.minecraft.client.MinecraftClient;
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

    private static final String FAULT_KEY = "mixin.blockanim";

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void elara$noBlockAnim(CallbackInfo ci) {
        if (Fault.off(FAULT_KEY)) return;
        try {
            if (Gate.loading()) return;
            // Ngoài thế giới không tắt hoạt ảnh: để game tự chạy như bình thường
            if (MinecraftClient.getInstance().world == null) return;
            if (ElaraConfig.isOn(Feature.NO_BLOCK_ANIM)) ci.cancel();
        } catch (Throwable t) {
            Fault.fail(FAULT_KEY, t);
        }
    }
}
