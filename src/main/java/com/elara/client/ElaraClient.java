package com.elara.client;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.core.LagFix;
import com.elara.client.hud.LyricsHud;
import com.elara.client.music.MusicPlayer;
import com.elara.client.hud.DynamicIslandHud;
import com.elara.client.hud.CrosshairHud;
import com.elara.client.hud.ElementsHud;
import com.elara.client.fx.Effects;
import com.elara.client.fx.HitFx;
import com.elara.client.fx.KnifeAnim;
import com.elara.client.fx.PlayerTracker;
import com.elara.client.hud.KeyTracker;
import com.elara.client.ui.ElaraMenuScreen;
import com.elara.client.ui.IosSplashScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.util.ActionResult;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ElaraClient implements ClientModInitializer {
    public static final String MOD_ID = "elaraclient";
    public static final String AUTHOR = "Lan Anh";

    private static KeyBinding menuKey;
    private static boolean splashDone = false;
    private static boolean lagSynced = false;
    private static boolean lastFocused = true;

    @Override
    public void onInitializeClient() {
        ElaraConfig.load();
        AuthorGuard.isValid(); // kiểm tra ngay khi khởi động để log sớm

        // Mặc định mở menu bằng phím Right Shift
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elaraclient.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.category.elaraclient.main"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CullingManager.tick();
            KeyTracker.tick(client);
            PlayerTracker.tick(client);
            KnifeAnim.tick(client);
            HitFx.tick();
            MusicPlayer.tick(ElaraConfig.isOn(Feature.MUSIC_PLAYER));

            // Đổi focus cửa sổ thì áp lại Fix Lag (giới hạn FPS khi không focus)
            boolean focused = client.isWindowFocused();
            if (focused != lastFocused) {
                lastFocused = focused;
                LagFix.sync(client);
            }

            // Áp dụng Fix Lag một lần khi game khởi động
            if (!lagSynced) {
                lagSynced = true;
                LagFix.sync(client);
            }

            // Splash kiểu iOS: chạy mỗi lần mở game, rồi quay lại màn hình gốc
            if (!splashDone && client.currentScreen instanceof TitleScreen) {
                splashDone = true;
                Screen next = client.currentScreen;
                client.setScreen(new IosSplashScreen(next));
                return;
            }

            while (menuKey.wasPressed()) {
                if (!AuthorGuard.isValid()) continue;
                // Mở được cả ở màn hình chính, không cần vào server
                Screen cur = client.currentScreen;
                if (cur == null || cur instanceof TitleScreen) {
                    client.setScreen(new ElaraMenuScreen(cur));
                }
            }
        });

        // Dynamic Island luôn vẽ trên đầu màn hình
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            DynamicIslandHud.render(context);
            ElementsHud.render(context);
            CrosshairHud.render(context);
            LyricsHud.render(context);
        });

        // Hiệu ứng trong thế giới: vòng nhảy, cánh, vòng tầm đánh, mưa 3D, hiệu ứng đánh trúng
        WorldRenderEvents.AFTER_TRANSLUCENT.register(Effects::render);

        // Đánh trúng thực thể: tạo hiệu ứng tại vị trí mục tiêu (không chặn hành động)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (ElaraConfig.isOn(Feature.HIT_EFFECT)) {
                HitFx.spawn(entity);
            }
            return ActionResult.PASS;
        });
    }
}
