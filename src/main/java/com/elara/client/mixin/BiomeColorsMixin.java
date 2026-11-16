package com.elara.client.mixin;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.BiomeColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Elara Lite Shader: pha mặt nước với xanh ngọc, kèm lấp lánh nhẹ theo tọa độ khối.
 * Màu nước được ghi vào mesh chunk khi dựng, nên bật hoặc tắt tính năng thì ElaraMenuScreen dựng lại chunk.
 * Nếu game không có hàm getWaterColor đúng chữ ký thì mixin này bỏ qua (require = 0), game vẫn chạy.
 */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {

    private static final int TURQUOISE_R = 0x30;
    private static final int TURQUOISE_G = 0xD5;
    private static final int TURQUOISE_B = 0xC8;
    /** Tỉ lệ trộn với xanh ngọc: 0 là màu gốc, 1 là xanh ngọc thuần */
    private static final float MIX = 0.5f;

    @Inject(method = "getWaterColor(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void elara$turquoiseWater(BlockRenderView world, BlockPos pos,
                                             CallbackInfoReturnable<Integer> cir) {
        if (!AuthorGuard.isValid()) return;
        if (!ElaraConfig.isOn(Feature.LITE_SHADER)) return;

        int base = cir.getReturnValue();
        int br = (base >> 16) & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = base & 0xFF;
        // Lấp lánh: hệ số sáng từ 0.96 đến 1.04, đổi theo tọa độ khối để mặt nước không phẳng màu
        int h = (pos.getX() * 73856093 ^ pos.getZ() * 19349663) & 0xFF;
        float shimmer = 0.96f + h / 255f * 0.08f;
        int r = clamp((br + (TURQUOISE_R - br) * MIX) * shimmer);
        int g = clamp((bg + (TURQUOISE_G - bg) * MIX) * shimmer);
        int b = clamp((bb + (TURQUOISE_B - bb) * MIX) * shimmer);
        cir.setReturnValue((r << 16) | (g << 8) | b);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }
}
