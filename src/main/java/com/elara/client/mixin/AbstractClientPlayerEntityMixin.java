package com.elara.client.mixin;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Guard;
import com.elara.client.skin.SkinStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thay skin của người chơi local bằng skin tùy chỉnh. Chỉ đổi texture và kiểu tay, giữ nguyên cape và elytra.
 * require = 0: nếu bản game không có phương thức này thì mixin bỏ qua, game vẫn chạy với skin gốc.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true, require = 0)
    private void elara$customSkin(CallbackInfoReturnable<SkinTextures> cir) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            // Chỉ người chơi local: không có nguồn skin tùy chỉnh cho người khác
            if ((Object) this != client.player) return;
            Identifier id = SkinStore.activeTexture();
            if (id == null) return;
            SkinTextures orig = cir.getReturnValue();
            if (orig == null) return;
            SkinTextures.Model model = ElaraConfig.skinSlim() ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE;
            cir.setReturnValue(new SkinTextures(id, orig.textureUrl(), orig.capeTexture(),
                    orig.elytraTexture(), model, orig.secure()));
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            // Lỗi thì giữ skin gốc của game, không làm sập
        }
    }
}
