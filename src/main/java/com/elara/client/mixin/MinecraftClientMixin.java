package com.elara.client.mixin;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Guard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Điểm ghi cấu hình duy nhất ngoài lúc tắt game: mỗi lần màn hình đổi (đóng menu Elara, mở màn hình khác)
 * thì ghi cấu hình nếu có thay đổi. Không chạy theo tick, nên không tốn CPU khi không đổi gì.
 * require = 0: nếu không khớp thì chỉ mất việc tự ghi lúc đổi màn hình, lúc tắt game vẫn ghi.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"), require = 0)
    private void elara$flushConfig(CallbackInfo ci) {
        try {
            ElaraConfig.flushIfDirty();
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
        }
    }
}
