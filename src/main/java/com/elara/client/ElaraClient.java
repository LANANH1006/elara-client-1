package com.elara.client;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.LagFix;
import com.elara.client.hud.DynamicIslandHud;
import com.elara.client.ui.ElaraMenuScreen;
import com.elara.client.ui.IosSplashScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ElaraClient implements ClientModInitializer {
    public static final String MOD_ID = "elaraclient";
    public static final String AUTHOR = "Lan Anh";

    private static KeyBinding menuKey;
    private static boolean splashDone = false;
    private static boolean lagSynced = false;

    @Override
    public void onInitializeClient() {
        ElaraConfig.load();
        AuthorGuard.isValid(); // kiểm tra ngay khi khởi động để log sớm

        // Mặc định mở menu bằng phím Right Shift
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elaraclient.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CullingManager.tick();

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
                if (client.currentScreen == null) {
                    client.setScreen(new ElaraMenuScreen(null));
                }
            }
        });

        // Dynamic Island luôn vẽ trên đầu màn hình
        HudRenderCallback.EVENT.register((context, tickCounter) -> DynamicIslandHud.render(context));
    }
}
