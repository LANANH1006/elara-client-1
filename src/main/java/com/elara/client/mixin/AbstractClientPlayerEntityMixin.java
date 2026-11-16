package com.elara.client.mixin;

import com.elara.client.core.Gate;

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
 *
 * An toàn khi vào map: nếu chưa bật skin tùy chỉnh thì thoát ngay, không chạm vào gì. Mọi việc tạo texture
 * đều chạy trên render thread (trong SkinStore). Khi texture chưa sẵn sàng thì trả về skin gốc của game.
 * Lỗi bất kỳ thì giữ skin gốc, không làm sập. require = 0: không khớp thì bỏ qua.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    /** Đã in lỗi skin một lần, tránh spam log vì hàm này chạy mỗi khung hình */
    private static boolean loggedSkinError;

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true, require = 0)
    private void elara$customSkin(CallbackInfoReturnable<SkinTextures> cir) {
        try {
            if (Gate.loading()) return;
            if (!ElaraConfig.hasCustomSkin()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
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
            // Lỗi thì giữ skin gốc của game, không làm sập. Chỉ ghi log lần đầu để không spam mỗi khung hình.
            if (!loggedSkinError) {
                loggedSkinError = true;
                System.out.println("[Elara] lỗi skin tùy chỉnh, dùng skin gốc: " + t);
                t.printStackTrace(System.out);
                System.out.flush();
            }
        }
    }
}
