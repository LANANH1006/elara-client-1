package com.elara.client.mixin;

import com.elara.client.badge.NameBadge;
import com.elara.client.core.AuthorGuard;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.core.Gate;
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

    private static final String FAULT_KEY = "mixin.badge.tab";

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void elara$badgeTab(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (Fault.off(FAULT_KEY)) return;
        try {
            if (!AuthorGuard.isValid()) return;
            if (Gate.loading()) return;
            if (!ElaraConfig.isOn(Feature.BADGE)) return;
            Text name = cir.getReturnValue();
            if (name != null) cir.setReturnValue(NameBadge.prefix(name));
        } catch (Throwable t) {
            Fault.fail(FAULT_KEY, t);
        }
    }
}
