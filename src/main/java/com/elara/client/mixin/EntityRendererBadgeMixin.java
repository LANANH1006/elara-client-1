package com.elara.client.mixin;

import com.elara.client.badge.NameBadge;
import com.elara.client.core.AuthorGuard;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.core.Gate;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Thêm logo Elara trước nametag của người chơi. Tên mob không có logo. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererBadgeMixin {

    private static final String FAULT_KEY = "mixin.badge.label";

    @ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true, require = 0)
    private Text elara$badgeLabel(Text text) {
        if (text == null || Fault.off(FAULT_KEY)) return text;
        try {
            if (!AuthorGuard.isValid()) return text;
            if (Gate.loading()) return text;
            if (!ElaraConfig.isOn(Feature.BADGE)) return text;
            return NameBadge.isPlayerName(text.getString()) ? NameBadge.prefix(text) : text;
        } catch (Throwable t) {
            Fault.fail(FAULT_KEY, t);
            return text;
        }
    }
}
