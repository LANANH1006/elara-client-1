package com.elara.client.core;

import com.elara.client.i18n.Lang;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Mọi tính năng bật/tắt được trong menu. Mặc định đều tắt. */
public enum Feature {
    LAG_RENDER(Group.FIX_LAG),
    LAG_ENTITIES(Group.FIX_LAG),
    LAG_PARTICLES(Group.FIX_LAG),
    LAG_GPU_SAVER(Group.FIX_LAG),
    LAG_CLOUDS(Group.FIX_LAG),
    LAG_BIOME(Group.FIX_LAG),
    LAG_MIPMAP(Group.FIX_LAG),
    LAG_FOCUS_FPS(Group.FIX_LAG),

    RAIN_3D(Group.EFFECTS),
    VIRTUAL_WINGS(Group.EFFECTS),
    JUMP_RING(Group.EFFECTS),
    HEAD_ACCESSORY(Group.EFFECTS),

    KNIFE_ANIM(Group.KNIVES),

    PIXEL_CROSSHAIR(Group.CROSSHAIR),
    PVP_REACH(Group.CROSSHAIR),
    HIT_EFFECT(Group.CROSSHAIR),

    MUSIC_PLAYER(Group.MISC),
    LYRICS_ISLAND(Group.MISC),
    KEYSTROKES(Group.MISC),
    SYSINFO(Group.MISC);

    public enum Group {
        FIX_LAG(2),
        EFFECTS(3),
        KNIVES(4),
        CROSSHAIR(5),
        MISC(6),
        PACK(7);

        /** Phase sẽ viết code thật cho tab này */
        public final int phase;

        Group(int phase) {
            this.phase = phase;
        }

        public String label() {
            return Lang.get("tab." + name().toLowerCase(Locale.ROOT));
        }
    }

    public final Group group;

    Feature(Group group) {
        this.group = group;
    }

    public String label() {
        return Lang.get("feature." + name().toLowerCase(Locale.ROOT));
    }

    public static List<Feature> inGroup(Group g) {
        return Arrays.stream(values()).filter(f -> f.group == g).toList();
    }
}
