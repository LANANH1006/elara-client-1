package com.elara.client.badge;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Logo Elara đặt trước tên người chơi. Logo là một glyph của font riêng
 * (assets/elaraclient/font/badge.json), nên đi chung đường vẽ chữ của game:
 * không cần tính lại ma trận nametag và vẫn đúng khi người chơi ở xa.
 */
public final class NameBadge {
    private static final Identifier FONT = Identifier.of("elaraclient", "badge");
    private static final String GLYPH = "";

    private NameBadge() {}

    /** Thêm logo trước một tên. Tắt tính năng thì trả lại đúng tên gốc. */
    public static Text prefix(Text name) {
        if (name == null || !ElaraConfig.isOn(Feature.BADGE)) return name;
        Text icon = Text.literal(GLYPH).setStyle(Style.EMPTY.withFont(FONT));
        return Text.empty().append(icon).append(Text.literal(" ")).append(name);
    }

    /** true nếu chuỗi trùng tên một người chơi đang có trên server */
    public static boolean isPlayerName(String text) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.getNetworkHandler() == null || text == null) return false;
        for (PlayerListEntry entry : c.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().getName().equals(text)) return true;
        }
        return false;
    }
}
