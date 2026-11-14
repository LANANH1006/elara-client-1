package com.elara.client.hud;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.i18n.Lang;
import com.elara.client.ui.IosStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** HUD phím (keystrokes) và thông số hệ thống. Vị trí, tỉ lệ, màu lấy từ bảng cài đặt chuột phải. */
public final class ElementsHud {
    private static final int BG = 0x99000000;
    private static final int KEY = 18;
    private static final int GAP = 2;
    private static final int STEP = KEY + GAP;

    private ElementsHud() {}

    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null || client.options.hudHidden || client.player == null) return;
        if (ElaraConfig.isOn(Feature.KEYSTROKES)) drawKeystrokes(ctx, client);
        if (ElaraConfig.isOn(Feature.SYSINFO)) drawSysInfo(ctx, client);
    }

    /** Đưa gốc tọa độ về góc trên trái của HUD đã áp dụng vị trí và tỉ lệ. Nhớ gọi pop(). */
    private static void begin(DrawContext ctx, MinecraftClient client, Feature f) {
        ElaraConfig.Layout l = ElaraConfig.layout(f);
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        float s = l.scalePct / 100f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sw * l.xPct / 100.0, sh * l.yPct / 100.0, 0.0);
        ctx.getMatrices().scale(s, s, 1f);
    }

    private static void drawKeystrokes(DrawContext ctx, MinecraftClient client) {
        begin(ctx, client, Feature.KEYSTROKES);
        TextRenderer tr = client.textRenderer;
        var opts = client.options;

        key(ctx, tr, STEP, 0, KEY, KEY, "W", opts.forwardKey.isPressed(), "W");
        key(ctx, tr, 0, STEP, KEY, KEY, "A", opts.leftKey.isPressed(), "A");
        key(ctx, tr, STEP, STEP, KEY, KEY, "S", opts.backKey.isPressed(), "S");
        key(ctx, tr, 2 * STEP, STEP, KEY, KEY, "D", opts.rightKey.isPressed(), "D");

        int half = (3 * KEY + 2 * GAP - GAP) / 2;
        key(ctx, tr, 0, 2 * STEP, half, KEY, "LMB", opts.attackKey.isPressed(), "LMB");
        key(ctx, tr, half + GAP, 2 * STEP, half, KEY, "RMB", opts.useKey.isPressed(), "RMB");
        key(ctx, tr, 0, 3 * STEP, 3 * KEY + 2 * GAP, KEY, "SPACE", opts.jumpKey.isPressed(), "SPACE");

        ctx.drawText(tr, Text.literal("CPS " + KeyTracker.cpsLeft() + " | " + KeyTracker.cpsRight()),
                0, 4 * STEP + 2, IosStyle.TEXT, true);
        ctx.getMatrices().pop();
    }

    private static void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
                            String label, boolean pressed, String id) {
        float p = IosStyle.progress("ks:" + id, pressed);
        int bg = IosStyle.lerpColor(BG, ElaraConfig.color(Feature.KEYSTROKES), p);
        IosStyle.rounded(ctx, x, y, w, h, 4, bg);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(label), x + w / 2, y + (h - 8) / 2, IosStyle.TEXT);
    }

    private static void drawSysInfo(DrawContext ctx, MinecraftClient client) {
        begin(ctx, client, Feature.SYSINFO);
        TextRenderer tr = client.textRenderer;
        PlayerEntity p = client.player;

        List<String> lines = new ArrayList<>();
        lines.add(Lang.get("sys.fps") + ": " + client.getCurrentFps());
        lines.add(Lang.get("sys.ping") + ": " + ping(client, p) + " ms");
        lines.add(Lang.get("sys.pos") + ": " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ());
        lines.add(Lang.get("sys.facing") + ": " + facing(p.getYaw()));
        lines.add(Lang.get("sys.armor") + ": " + armor(p));

        int maxW = 0;
        for (String line : lines) maxW = Math.max(maxW, tr.getWidth(line));
        int boxH = lines.size() * 10 + 6;
        IosStyle.rounded(ctx, 0, 0, maxW + 12, boxH, 6, BG);
        for (int i = 0; i < lines.size(); i++) {
            ctx.drawText(tr, Text.literal(lines.get(i)), 6, 5 + i * 10,
                    ElaraConfig.color(Feature.SYSINFO), true);
        }
        ctx.getMatrices().pop();
    }

    private static String ping(MinecraftClient client, PlayerEntity p) {
        if (client.getNetworkHandler() == null) return "--";
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(p.getUuid());
        return entry == null ? "--" : String.valueOf(entry.getLatency());
    }

    /** Hướng nhìn theo 4 phương: S, W, N, E */
    private static String facing(float yaw) {
        String[] names = {"S", "W", "N", "E"};
        int idx = Math.floorMod(Math.round(yaw / 90f), 4);
        return names[idx];
    }

    /** Độ bền còn lại của 4 món giáp, từ mũ đến giày. "-" nếu không mặc. */
    private static String armor(PlayerEntity p) {
        StringBuilder sb = new StringBuilder();
        for (int slot = 3; slot >= 0; slot--) {
            ItemStack st = p.getInventory().getArmorStack(slot);
            if (st.isEmpty() || !st.isDamageable() || st.getMaxDamage() <= 0) {
                sb.append("-");
            } else {
                int pct = (st.getMaxDamage() - st.getDamage()) * 100 / st.getMaxDamage();
                sb.append(pct).append("%");
            }
            if (slot > 0) sb.append(" ");
        }
        return sb.toString();
    }
}
