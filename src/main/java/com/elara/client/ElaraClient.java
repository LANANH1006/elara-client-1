package com.elara.client;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.core.LagFix;
import com.elara.client.core.Warmup;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.elara.client.hud.LyricsHud;
import com.elara.client.music.MusicPlayer;
import com.elara.client.hud.DynamicIslandHud;
import com.elara.client.hud.CrosshairHud;
import com.elara.client.hud.ElementsHud;
import com.elara.client.fx.CombatFx;
import com.elara.client.fx.Effects;
import com.elara.client.fx.BuildGuide;
import com.elara.client.fx.PlayerMotion;
import com.elara.client.fx.HitFx;
import com.elara.client.fx.KnifeAnim;
import com.elara.client.fx.SoftShadow;
import com.elara.client.fx.WorldFx;
import com.elara.client.ui.ChatKeyboard;
import com.elara.client.skin.SkinStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.gui.screen.ChatScreen;
import com.elara.client.fx.PlayerTracker;
import com.elara.client.hud.KeyTracker;
import com.elara.client.i18n.Lang;
import com.elara.client.hud.AfkOverlay;
import com.elara.client.ui.ElaraMenuScreen;
import com.elara.client.ui.IosSplashScreen;
import com.elara.client.ui.SceneScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
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
    private static KeyBinding inspectKey;
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

        // Phím Inspect (G): xoay vũ khí đang cầm, khi bật tính năng Dao
        inspectKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elaraclient.inspect",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.category.elaraclient.main"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Warmup.tick();
            CullingManager.tick();
            KeyTracker.tick(client);
            PlayerTracker.tick(client);
            PlayerMotion.tick(client);
            BuildGuide.tick(client);
            KnifeAnim.tick(client);
            // Tính toán thế giới và bóng: có try/catch riêng. Vài tick đầu sau khi vào map thì tạm dừng.
            if (Warmup.ready()) {
                WorldFx.tick(client);
                SoftShadow.tick(client);
            }
            // Cấu hình không ghi theo tick: chỉ ghi khi đổi màn hình hoặc khi game tắt (xem ElaraConfig)
            // Nhận skin tải về và đẩy texture khi có đổi (việc upload chạy trên luồng này, tức render thread)
            SkinStore.tickMain();
            while (inspectKey.wasPressed()) KnifeAnim.inspect(client);
            HitFx.tick();
            if (Warmup.ready()) CombatFx.tick(client);
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
            AfkOverlay.render(context); // phủ cuối cùng, nằm trên mọi HUD
        });

        // Hiệu ứng trong thế giới: vòng nhảy, cánh, vòng tầm đánh, mưa 3D, hiệu ứng đánh trúng
        WorldRenderEvents.AFTER_TRANSLUCENT.register(Effects::render);

        // Nút Scene ở góc dưới phải màn hình chính: mở bảng chọn nền ảnh tĩnh hoặc cảnh 3D
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (!(screen instanceof TitleScreen)) return;
            ButtonWidget scene = ButtonWidget.builder(Text.literal(Lang.get("title.scene")),
                            b -> client.setScreen(new SceneScreen(screen)))
                    .dimensions(sw - 80, sh - 26, 70, 20)
                    .build();
            Screens.getButtons(screen).add(scene);
        });

        // Bàn phím ảo và thanh nút nhanh trên màn hình chat (tính năng Chat Touch)
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (screen instanceof ChatScreen) ChatKeyboard.attach(screen);
        });

        // Đánh trúng thực thể: tạo hiệu ứng tại vị trí mục tiêu (không chặn hành động)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (ElaraConfig.isOn(Feature.HIT_EFFECT)) {
                HitFx.spawn(entity);
            }
            if (Warmup.ready()) CombatFx.onAttack(player, entity);
            return ActionResult.PASS;
        });

        // Vừa vào server hoặc map: đặt thời gian chờ ngắn, hiệu ứng nặng sẽ bật lại sau đó
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> Warmup.onJoin());

        // Khi game tắt: ghi đồng bộ cấu hình và nét vẽ skin, để không mất thay đổi cuối cùng
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            ElaraConfig.flushNow();
            SkinStore.flushNow();
        });
    }
}
