package com.elara.client.core;

import com.elara.client.i18n.Lang;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Mọi tính năng bật/tắt được trong menu. Mặc định đều tắt. */
public enum Feature {
    LAG_ENTITIES(Group.FIX_LAG),
    LAG_PARTICLES(Group.FIX_LAG),
    NO_FOG(Group.FIX_LAG),
    NO_BLOCK_ANIM(Group.FIX_LAG),
    AFK_SAVER(Group.FIX_LAG),
    LAG_BLOCK_ENTITIES(Group.FIX_LAG),

    RAIN_3D(Group.EFFECTS),
    VIRTUAL_WINGS(Group.EFFECTS),
    JUMP_RING(Group.EFFECTS),
    HEAD_ACCESSORY(Group.EFFECTS),
    FOOTSTEP_AURA(Group.EFFECTS),
    CAPE(Group.EFFECTS),
    BODY_GLOW(Group.EFFECTS),
    RIBBON_TRAIL(Group.EFFECTS),
    AIRPLANE_MORPH(Group.EFFECTS),

    KNIFE_ANIM(Group.KNIVES),
    SWORD_TRAIL(Group.KNIVES),
    WEAPON_3D(Group.KNIVES),

    PIXEL_CROSSHAIR(Group.CROSSHAIR),
    PVP_REACH(Group.CROSSHAIR),
    HIT_EFFECT(Group.CROSSHAIR),
    TARGET_HUD(Group.CROSSHAIR),
    SHIELD_BREAKER(Group.CROSSHAIR),
    HIT_RIPPLE(Group.CROSSHAIR),
    AFTERGLOW(Group.CROSSHAIR),
    SHOCKWAVE(Group.CROSSHAIR),
    LOW_FIRE(Group.CROSSHAIR),

    WORLD_SKY(Group.WORLD),
    AMBIENCE(Group.WORLD),
    RAIN_RIPPLE(Group.WORLD),
    CHROMA_WORLD(Group.WORLD),
    MIST_GLASS(Group.WORLD),
    SOFT_SHADOW(Group.WORLD),
    LITE_SHADER(Group.WORLD),

    MUSIC_PLAYER(Group.MISC),
    LYRICS_ISLAND(Group.MISC),
    LYRICS_SKY(Group.MISC),
    KEYSTROKES(Group.MISC),
    SYSINFO(Group.MISC),
    BADGE(Group.MISC),
    CHAT_TOUCH(Group.MISC),

    BUILD_GUIDE(Group.BUILDER);

    public enum Group {
        FIX_LAG(2),
        EFFECTS(3),
        WORLD(9),
        KNIVES(4),
        CROSSHAIR(5),
        MISC(6),
        BUILDER(7),
        PACK(8);

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
