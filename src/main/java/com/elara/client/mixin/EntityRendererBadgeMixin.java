package com.elara.client.mixin;

import com.elara.client.badge.NameBadge;
import com.elara.client.core.AuthorGuard;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Thêm logo Elara trước nametag của người chơi. Tên mob không có logo. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererBadgeMixin {

    @ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true, require = 0)
    private Text elara$badgeLabel(Text text) {
        if (text == null || !AuthorGuard.isValid()) return text;
        return NameBadge.isPlayerName(text.getString()) ? NameBadge.prefix(text) : text;
    }
}
