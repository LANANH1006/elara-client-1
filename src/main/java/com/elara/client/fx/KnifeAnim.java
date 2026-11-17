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
import net.minecraft.util.math.Vec3d;

/**
 * Hoạt ảnh dao kiểu CS2: chỉnh vị trí, nghiêng, kích thước tay cầm vũ khí,
 * xoay 360° khi đổi sang vũ khí khác hoặc bấm phím đổi tay (F) hoặc Inspect (G).
 *
 * Múa khi rảnh: nếu không di chuyển, không đánh, không bấm gì và không đổi slot trong IDLE_MS,
 * vũ khí sẽ múa một động tác ngắn mỗi CYCLE_MS. Mọi hành động đều ngắt múa và trả về
 * tư thế cầm trong RETURN_MS, để không bị giật.
 */
public final class KnifeAnim {
    private static final long SPIN_MS = 600L;
    /** Đứng yên bao lâu thì bắt đầu múa */
    public static final long IDLE_MS = 5000L;
    /** Mỗi động tác múa kéo dài bao lâu */
    public static final long FLOURISH_MS = 900L;
    /** Chu kỳ lặp lại động tác khi vẫn đứng yên, nằm trong khoảng 5 đến 6 giây */
    public static final long CYCLE_MS = 5500L;
    /** Thời gian chuyển mượt về tư thế cầm, và thời gian ramp vào múa */
    public static final long RETURN_MS = 250L;

    private static long spinStart = -1L;
    private static int lastSlot = -1;
    private static boolean lastSwap = false;
    /** Lần cuối người chơi có hành động (di chuyển, đánh, đổi slot, bấm phím, mở menu) */
    private static long lastActive = 0L;
    /** Thời điểm và pha múa lúc bị ngắt, để chuyển mượt về tư thế cầm */
    private static long cutAt = 0L;
    private static float cutPhase = 0f;

    private static final float[] NO_POSE = {0f, 0f};

    private KnifeAnim() {}

    /** Gọi mỗi tick: phát hiện đổi slot, bấm phím đổi tay, di chuyển hoặc bấm phím */
    public static void tick(MinecraftClient client) {
        if (client.player == null || client.options == null) {
            lastSlot = -1;
            lastSwap = false;
            return;
        }
        long now = System.currentTimeMillis();
        int slot = client.player.getInventory().selectedSlot;
        boolean swap = client.options.swapHandsKey.isPressed();
        boolean weapon = isWeapon(client.player.getMainHandStack());
        boolean slotChanged = lastSlot != -1 && slot != lastSlot;
        boolean swapPressed = swap && !lastSwap;
        if (weapon && (slotChanged || swapPressed)) {
            spinStart = now;
        }
        lastSlot = slot;
        lastSwap = swap;

        boolean busy = slotChanged || swapPressed || client.currentScreen != null
                || moving(client) || pressing(client);
        if (busy) touch(now);
    }

    /** Bấm phím Inspect: xoay vũ khí đang cầm một vòng. Hiện khi bật Vũ khí 3D hoặc Hoạt ảnh dao. */
    public static void inspect(MinecraftClient client) {
        if (client.player != null && isWeapon(client.player.getMainHandStack())) {
            long now = System.currentTimeMillis();
            spinStart = now;
            touch(now);
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

    /**
     * Dáng múa khi rảnh tay: {pha, trọng số}. Pha từ 0 đến 1 trong một động tác, 1 khi đã xong.
     * Trọng số từ 0 đến 1: 0 nghĩa là không múa, nên vũ khí về tư thế cầm.
     */
    public static float[] idlePose() {
        long now = System.currentTimeMillis();
        long idle = now - lastActive;
        if (idle >= IDLE_MS) {
            float ramp = Math.min(1f, (idle - IDLE_MS) / (float) RETURN_MS);
            return new float[] {phaseAt(idle), ramp};
        }
        if (cutAt > 0L && now - cutAt < RETURN_MS) {
            return new float[] {cutPhase, 1f - (now - cutAt) / (float) RETURN_MS};
        }
        return NO_POSE;
    }

    /** Pha múa tại thời điểm đã đứng yên được idle mili giây */
    private static float phaseAt(long idle) {
        if (idle < IDLE_MS) return 0f;
        long into = (idle - IDLE_MS) % CYCLE_MS;
        return Math.min(1f, into / (float) FLOURISH_MS);
    }

    /** Ghi nhận một hành động. Nếu đang múa thì nhớ pha hiện tại để chuyển mượt về tư thế cầm. */
    private static void touch(long now) {
        if (now - lastActive >= IDLE_MS) {
            cutPhase = phaseAt(now - lastActive);
            cutAt = now;
        }
        lastActive = now;
    }

    private static boolean moving(MinecraftClient client) {
        Vec3d v = client.player.getVelocity();
        return v.lengthSquared() > 0.0004 || !client.player.isOnGround();
    }

    private static boolean pressing(MinecraftClient client) {
        var o = client.options;
        return o.attackKey.isPressed() || o.useKey.isPressed() || o.jumpKey.isPressed()
                || o.sneakKey.isPressed() || o.forwardKey.isPressed() || o.backKey.isPressed()
                || o.leftKey.isPressed() || o.rightKey.isPressed();
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
