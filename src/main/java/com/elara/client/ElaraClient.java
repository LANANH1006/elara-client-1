package com.elara.client;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Feature;
import com.elara.client.core.Diag;
import com.elara.client.core.Guard;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import com.elara.client.fx.PlayerTracker;
import com.elara.client.hud.KeyTracker;
import com.elara.client.hud.AfkOverlay;
import com.elara.client.ui.ElaraMenuScreen;
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

    /**
     * Chế độ cô lập lỗi treo ở thanh tải Mojang. Khi true, chỉ đăng ký nút [Elara] ở sảnh chính.
     * Mọi callback gameplay (tick, HUD, thế giới, tấn công, vào map, tắt game) không được đăng ký.
     * Đặt false để bật lại đầy đủ.
     */
    static final boolean ISOLATE_TITLE_ONLY = true;

    private static KeyBinding menuKey;
    private static KeyBinding inspectKey;
    private static boolean titleLogged;

    @Override
    public void onInitializeClient() {
        Diag.log("onInitializeClient: bắt đầu");
        ElaraConfig.load();
        Diag.log("onInitializeClient: đã nạp cấu hình");
        AuthorGuard.isValid(); // kiểm tra ngay khi khởi động để log sớm
        Diag.log("onInitializeClient: đã kiểm tra bản quyền");

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

        Diag.log("onInitializeClient: đã đăng ký phím");

        // Màn hình chính chỉ có đúng một nút nhỏ [Elara] ở góc dưới phải, mở Menu Elara.
        registerTitleButton();

        if (ISOLATE_TITLE_ONLY) {
            Diag.log("onInitializeClient: CHẾ ĐỘ CÔ LẬP, chỉ nút [Elara] ở sảnh chính");
            Diag.log("onInitializeClient: xong");
            return;
        }

        registerGameplayCallbacks();
        Diag.log("onInitializeClient: xong");
    }

    /** Nút [Elara] ở sảnh chính. Không vẽ nền, logo hay hiệu ứng nào khác: để Minecraft và Glaze tự vẽ. */
    private static void registerTitleButton() {
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            try {
                if (!(screen instanceof TitleScreen)) return;
                if (!titleLogged) {
                    titleLogged = true;
                    Diag.log("sảnh chính: thêm nút Elara");
                }
                ButtonWidget elara = ButtonWidget.builder(Text.literal("Elara"),
                                b -> client.setScreen(new ElaraMenuScreen(screen)))
                        .dimensions(sw - 58, sh - 24, 50, 18)
                        .build();
                Screens.getButtons(screen).add(elara);
            } catch (Throwable t) {
                report("nút Elara ở sảnh chính", t);
            }
        });
    }

    /** Các callback gameplay. Chỉ chạy khi ISOLATE_TITLE_ONLY là false. */
    private static void registerGameplayCallbacks() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                onClientTick(client);
            } catch (Throwable t) {
                report("tick", t);
            }
        });

        // Dynamic Island luôn vẽ trên đầu màn hình, chỉ khi đã vào thế giới
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            try {
                if (MinecraftClient.getInstance().world == null) return;
                DynamicIslandHud.render(context);
                ElementsHud.render(context);
                CrosshairHud.render(context);
                LyricsHud.render(context);
                AfkOverlay.render(context); // phủ cuối cùng, nằm trên mọi HUD
            } catch (Throwable t) {
                report("HUD", t);
            }
        });

        // Hiệu ứng trong thế giới: vòng nhảy, cánh, vòng tầm đánh, mưa 3D, hiệu ứng đánh trúng
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            try {
                if (MinecraftClient.getInstance().world != null) Effects.render(ctx);
            } catch (Throwable t) {
                report("hiệu ứng thế giới", t);
            }
        });

        // Bàn phím ảo và thanh nút nhanh trên màn hình chat (tính năng Chat Touch)
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            try {
                if (screen instanceof ChatScreen) ChatKeyboard.attach(screen);
            } catch (Throwable t) {
                report("bàn phím chat", t);
            }
        });

        // Đánh trúng thực thể: tạo hiệu ứng tại vị trí mục tiêu (không chặn hành động)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            try {
                if (ElaraConfig.isOn(Feature.HIT_EFFECT)) HitFx.spawn(entity);
                if (Warmup.ready()) CombatFx.onAttack(player, entity);
            } catch (Throwable t) {
                report("đánh trúng", t);
            }
            return ActionResult.PASS;
        });

        // Vừa vào server hoặc map: đặt thời gian chờ ngắn, hiệu ứng nặng sẽ bật lại sau đó
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            try {
                Warmup.onJoin();
            } catch (Throwable t) {
                report("vào máy chủ", t);
            }
        });

        // Khi game tắt: ghi đồng bộ cấu hình và nét vẽ skin, để không mất thay đổi cuối cùng
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            try {
                ElaraConfig.flushNow();
                SkinStore.flushNow();
            } catch (Throwable t) {
                report("tắt game", t);
            }
        });
    }

    /** Mỗi tick. Chưa vào thế giới thì chỉ chạy phần nhẹ. */
    private static void onClientTick(MinecraftClient client) {
        // Chưa vào thế giới (màn hình tải Mojang, sảnh chính): chỉ chạy phần nhẹ. Không tick hiệu ứng,
        // không chạm render, không chạm âm thanh (OpenAL / javax.sound).
        if (client.world == null) {
            ElaraConfig.tick();
            while (inspectKey.wasPressed()) { /* phím G chỉ có tác dụng trong thế giới */ }
            MusicPlayer.tick(false);
            handleMenuKey(client);
            return;
        }

        Warmup.tick();
        CullingManager.tick();
        KeyTracker.tick(client);
        PlayerTracker.tick(client);
        PlayerMotion.tick(client);
        BuildGuide.tick(client);
        KnifeAnim.tick(client);
        // Tính toán thế giới và bóng: vài tick đầu sau khi vào map thì tạm dừng
        if (Warmup.heavy()) {
            WorldFx.tick(client);
            SoftShadow.tick(client);
        }
        // Cấu hình: chỉ kiểm tra một cờ, ghi sau khoảng 300 ms kể từ thay đổi cuối (xem ElaraConfig)
        ElaraConfig.tick();
        // Nhận skin tải về và đẩy texture khi có đổi (việc upload chạy trên luồng này, tức render thread)
        SkinStore.tickMain();
        while (inspectKey.wasPressed()) KnifeAnim.inspect(client);
        HitFx.tick();
        if (Warmup.heavy()) CombatFx.tick(client);
        MusicPlayer.tick(ElaraConfig.isOn(Feature.MUSIC_PLAYER));
        handleMenuKey(client);
    }

    /** Phím Right Shift mở Menu Elara. Mở được cả ở sảnh chính, không cần vào server. */
    private static void handleMenuKey(MinecraftClient client) {
        while (menuKey.wasPressed()) {
            if (!AuthorGuard.isValid()) continue;
            Screen cur = client.currentScreen;
            if (cur == null || cur instanceof TitleScreen) {
                client.setScreen(new ElaraMenuScreen(cur));
            }
        }
    }

    /**
     * Ghi lỗi của một callback ra log để chẩn đoán. In ra System.out để nằm cùng luồng với dòng [Elara].
     * OOM được văng tiếp, không bị nuốt.
     */
    static void report(String where, Throwable t) {
        Guard.rethrowIfFatal(t);
        System.out.println("[Elara] lỗi trong " + where + ": " + t);
        t.printStackTrace(System.out);
        System.out.flush();
    }
}
