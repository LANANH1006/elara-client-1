package com.elara.client.hud;

import com.elara.client.core.ElaraAssets;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.fx.CombatFx;
import com.elara.client.i18n.Lang;
import com.elara.client.ui.IosStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;

/**
 * Target HUD kính mờ kiểu iOS: đầu mục tiêu, tên, thanh máu và giáp bo góc, trạng thái khiên,
 * khoảng cách và kết quả THẮNG/THUA so với mình. Giữ mục tiêu tối đa 3.5 giây sau khi nhắm sang chỗ khác.
 * Vị trí và tỉ lệ kéo thả trong HUD Editor.
 */
final class TargetHud {
    static final int W = 156;
    static final int H = 58;
    private static final long HOLD_MS = 3500L;
    private static final int BAR_W = 104;
    private static final int TEXT_X = 46;

    private static LivingEntity target;
    private static long lastSeen;
    private static float shownHp = -1f;
    private static float shownArmor = -1f;
    private static boolean broken = false;

    private TargetHud() {}

    /** Gọi trong ElementsHud khi tính năng đang bật */
    static void draw(DrawContext ctx, MinecraftClient client) {
        if (broken || client.player == null) return;
        long now = System.currentTimeMillis();
        Entity hit = client.targetedEntity;
        if (hit instanceof LivingEntity le && le != client.player && le.isAlive()) {
            if (le != target) {
                target = le;
                shownHp = -1f;
                shownArmor = -1f;
            }
            lastSeen = now;
        }
        LivingEntity t = target;
        if (t == null || now - lastSeen > HOLD_MS || !t.isAlive()) return;
        try {
            drawPanel(ctx, client, t);
        } catch (RuntimeException ex) {
            broken = true;
            System.err.println("[Elara] Tắt Target HUD do lỗi: " + ex);
        }
    }

    private static void drawPanel(DrawContext ctx, MinecraftClient client, LivingEntity t) {
        PlayerEntity me = client.player;
        TextRenderer tr = client.textRenderer;
        int argb = ElaraConfig.color(Feature.TARGET_HUD);

        ElementsHud.begin(ctx, client, Feature.TARGET_HUD);
        try {
            HudBounds.record(Feature.TARGET_HUD, W, H);
            IosStyle.glassPanel(ctx, 0, 0, W, H, 12);
            drawHead(ctx, tr, t, 8, 8, 28, argb);

            // Lerp mượt: thanh máu và giáp trượt dần về giá trị thật
            float hp = t.getHealth();
            float max = Math.max(1f, t.getMaxHealth());
            shownHp = shownHp < 0 ? hp : shownHp + (hp - shownHp) * 0.2f;
            float armor = Math.min(20f, t.getArmor());
            shownArmor = shownArmor < 0 ? armor : shownArmor + (armor - shownArmor) * 0.2f;

            // Tên, và kết quả so với mình
            boolean winning = me.getHealth() >= hp;
            String verdict = Lang.get(winning ? "target.win" : "target.lose");
            int verdictCol = winning ? IosStyle.GREEN : 0xFFFF453A;
            int vw = tr.getWidth(verdict);
            ctx.drawText(tr, Text.literal(verdict), W - 8 - vw, 8, verdictCol, false);
            String name = trim(tr, t.getName().getString(), W - TEXT_X - vw - 12);
            ctx.drawText(tr, Text.literal(name), TEXT_X, 8, IosStyle.TEXT, true);

            // Máu: thanh bo góc
            IosStyle.rounded(ctx, TEXT_X, 21, BAR_W, 6, 3, IosStyle.GRAY);
            int hpW = Math.max(6, Math.round(BAR_W * Math.min(1f, shownHp / max)));
            IosStyle.rounded(ctx, TEXT_X, 21, hpW, 6, 3, argb);

            // Giáp: thanh mảnh màu xanh nhạt
            IosStyle.rounded(ctx, TEXT_X, 31, BAR_W, 4, 2, IosStyle.GRAY);
            if (shownArmor > 0.5f) {
                int aw = Math.max(4, Math.round(BAR_W * shownArmor / 20f));
                IosStyle.rounded(ctx, TEXT_X, 31, aw, 4, 2, 0xFF8AB4FF);
            }

            // Dòng dưới: khoảng cách, trạng thái khiên, máu số
            String dist = String.format(Locale.ROOT, "%.1fm", me.distanceTo(t));
            ctx.drawText(tr, Text.literal(dist), 8, 42, IosStyle.TEXT_DIM, false);
            drawShield(ctx, tr, t);
            String hpText = String.format(Locale.ROOT, "%.1f HP", hp);
            ctx.drawText(tr, Text.literal(hpText), W - 8 - tr.getWidth(hpText), 42, IosStyle.TEXT_DIM, false);
        } finally {
            ctx.getMatrices().pop();
        }
    }

    /** Đầu mục tiêu: lấy mặt và lớp mũ từ texture skin nếu là người chơi, ngược lại vẽ chữ cái đầu tên */
    private static void drawHead(DrawContext ctx, TextRenderer tr, LivingEntity t, int x, int y, int size, int argb) {
        Identifier skin = t instanceof AbstractClientPlayerEntity ap ? ap.getSkinTextures().texture() : null;
        if (skin == null) {
            IosStyle.rounded(ctx, x, y, size, size, 6, argb);
            String name = t.getName().getString();
            String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(letter), x + size / 2, y + (size - 8) / 2, IosStyle.TEXT);
            return;
        }
        // Mặt ở ô (8,8) cỡ 8x8, lớp mũ ở ô (40,8), phóng to bằng ma trận
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        float s = size / 8f;
        ctx.getMatrices().scale(s, s, 1f);
        ctx.drawTexture(RenderLayer::getGuiTextured, skin, 0, 0, 8f, 8f, 8, 8, 64, 64);
        ctx.drawTexture(RenderLayer::getGuiTextured, skin, 0, 0, 40f, 8f, 8, 8, 64, 64);
        ctx.getMatrices().pop();
    }

    /** Khiên đang bị phá thì hiện vết nứt và đếm ngược, không thì hiện bật hoặc tắt */
    private static void drawShield(DrawContext ctx, TextRenderer tr, LivingEntity t) {
        long left = CombatFx.shieldRemainingMs(t.getUuid());
        if (left > 0) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(TEXT_X, 40, 0);
            float s = 12f / 64f;
            ctx.getMatrices().scale(s, s, 1f);
            if (ElaraAssets.ensureShieldCrack()) {
                ctx.drawTexture(RenderLayer::getGuiTextured, ElaraAssets.SHIELD_CRACK, 0, 0, 0f, 0f, 64, 64, 64, 64);
            }
            ctx.getMatrices().pop();
            String txt = String.format(Locale.ROOT, "%.1fs", left / 1000.0);
            ctx.drawText(tr, Text.literal(txt), TEXT_X + 15, 42, 0xFFFFB347, false);
            return;
        }
        if (t instanceof PlayerEntity p) {
            boolean up = p.isBlocking();
            ctx.drawText(tr, Text.literal(Lang.get(up ? "target.shield_on" : "target.shield_off")),
                    TEXT_X, 42, up ? IosStyle.GREEN : IosStyle.TEXT_DIM, false);
        }
    }

    private static String trim(TextRenderer tr, String s, int maxPx) {
        if (tr.getWidth(s) <= maxPx) return s;
        String out = s;
        while (out.length() > 1 && tr.getWidth(out + "..") > maxPx) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
    }
}
