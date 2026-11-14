package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.RotationAxis;

/**
 * Hoạt ảnh dao kiểu CS2: chỉnh vị trí, nghiêng, kích thước tay cầm vũ khí,
 * và xoay 360° khi đổi sang vũ khí khác hoặc bấm phím đổi tay (F).
 */
public final class KnifeAnim {
    private static final long SPIN_MS = 600L;

    private static long spinStart = -1L;
    private static int lastSlot = -1;
    private static boolean lastSwap = false;

    private KnifeAnim() {}

    /** Gọi mỗi tick: phát hiện đổi slot hoặc bấm phím đổi tay khi đang cầm vũ khí */
    public static void tick(MinecraftClient client) {
        if (client.player == null || client.options == null) {
            lastSlot = -1;
            lastSwap = false;
            return;
        }
        int slot = client.player.getInventory().selectedSlot;
        boolean swap = client.options.swapHandsKey.isPressed();
        boolean weapon = isWeapon(client.player.getMainHandStack());
        boolean slotChanged = lastSlot != -1 && slot != lastSlot;
        boolean swapPressed = swap && !lastSwap;
        if (weapon && (slotChanged || swapPressed)) {
            spinStart = System.currentTimeMillis();
        }
        lastSlot = slot;
        lastSwap = swap;
    }

    /** Bấm phím Inspect: xoay vũ khí đang cầm một vòng. Hiện khi bật Vũ khí 3D hoặc Hoạt ảnh dao. */
    public static void inspect(MinecraftClient client) {
        if (client.player != null && isWeapon(client.player.getMainHandStack())) {
            spinStart = System.currentTimeMillis();
        }
    }

    public static boolean isWeapon(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem;
    }

    /** Áp biến đổi cho tay cầm vũ khí. Không bật tính năng hoặc không phải vũ khí thì không làm gì. */
    public static void apply(MatrixStack m, ItemStack item) {
        if (!ElaraConfig.isOn(Feature.KNIFE_ANIM) || !isWeapon(item)) return;
        ElaraConfig.Layout l = ElaraConfig.layout(Feature.KNIFE_ANIM);
        m.translate(l.get("hx", 0) / 100f, l.get("hy", 0) / 100f, l.get("hz", 0) / 100f);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(l.get("tilt", 0)));
        float s = l.scalePct / 100f;
        m.scale(s, s, s);
        float spin = spinAngle();
        if (spin != 0f) {
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
        }
    }

    /** Góc xoay hiện tại của hoạt ảnh, dùng cho vũ khí 3D. 0 khi không xoay. */
    public static float spinDegrees() {
        return spinAngle();
    }

    /** Góc xoay hiện tại, 0 đến 360 độ, theo đường cong mượt. 0 khi không xoay. */
    private static float spinAngle() {
        if (spinStart < 0) return 0f;
        long age = System.currentTimeMillis() - spinStart;
        if (age >= SPIN_MS) {
            spinStart = -1L;
            return 0f;
        }
        float t = age / (float) SPIN_MS;
        float eased = t * t * (3 - 2 * t);
        return 360f * eased;
    }
}
