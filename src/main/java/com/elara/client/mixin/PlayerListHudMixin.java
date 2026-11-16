package com.elara.client.mixin;

import com.elara.client.core.Gate;

import com.elara.client.badge.NameBadge;
import com.elara.client.core.AuthorGuard;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Thêm logo Elara trước tên người chơi trong bảng Tab */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void elara$badgeTab(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (!AuthorGuard.isValid()) return;
        if (Gate.loading()) return;
        if (!ElaraConfig.isOn(Feature.BADGE)) return;
        Text name = cir.getReturnValue();
        if (name != null) cir.setReturnValue(NameBadge.prefix(name));
    }
}
