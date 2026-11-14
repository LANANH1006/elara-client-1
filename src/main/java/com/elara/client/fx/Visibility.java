package com.elara.client.fx;

import com.elara.client.core.ElaraConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

/** Chế độ hiển thị cánh và vòng nhảy: chỉ mình tôi, mọi người gần đó, hoặc chỉ góc nhìn thứ 3 (F5). */
public final class Visibility {
    public static final int SELF = 0;
    public static final int NEARBY = 1;
    public static final int THIRD_PERSON = 2;

    private static final double NEARBY_RANGE_SQ = 48.0 * 48.0;

    private Visibility() {}

    /** Có vẽ hiệu ứng của {@code owner} đang ở (x, y, z) với chế độ trong {@code l} không */
    public static boolean allows(ElaraConfig.Layout l, MinecraftClient client, UUID owner,
                                 double x, double y, double z) {
        PlayerEntity me = client.player;
        if (me == null) return false;
        boolean local = owner.equals(me.getUuid());
        return switch (l.get("vis", SELF)) {
            case NEARBY -> me.squaredDistanceTo(x, y, z) <= NEARBY_RANGE_SQ;
            case THIRD_PERSON -> local && !client.options.getPerspective().isFirstPerson();
            default -> local;
        };
    }
}
