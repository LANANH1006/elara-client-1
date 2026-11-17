package com.elara.client;

import com.elara.client.core.AuthorGuard;
import com.elara.client.core.CullingManager;
import com.elara.client.core.Diag;
import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Fault;
import com.elara.client.core.Feature;
import com.elara.client.core.Gate;
import com.elara.client.core.Warmup;
import com.elara.client.fx.BuildGuide;
import com.elara.client.fx.CombatFx;
import com.elara.client.fx.Effects;
import com.elara.client.fx.HitFx;
import com.elara.client.fx.KnifeAnim;
import com.elara.client.fx.PlayerMotion;
import com.elara.client.fx.PlayerTracker;
import com.elara.client.fx.SoftShadow;
import com.elara.client.fx.WorldFx;
import com.elara.client.hud.AfkOverlay;
import com.elara.client.hud.CrosshairHud;
import com.elara.client.hud.DynamicIslandHud;
import com.elara.client.hud.ElementsHud;
import com.elara.client.hud.KeyTracker;
import com.elara.client.hud.LyricsHud;
import com.elara.client.music.MusicPlayer;
import com.elara.client.skin.SkinStore;
import com.elara.client.ui.ChatKeyboard;
import com.elara.client.ui.ElaraMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

public class ElaraClient implements ClientModInitializer {
    public static final String MOD_ID = "elaraclient";
    public static final String AUTHOR = "Lan Anh";

    /**
     * Chế độ cô lập lỗi treo ở thanh tải Mojang. Khi true, chỉ đăng ký nút [Elara] ở sảnh chính.
     * Mọi callback gameplay (tick, HUD, thế giới, tấn công, vào map, tắt game) không được đăng ký.
     * Đặt false để bật lại đầy đủ.
     */
    static final boolean ISOLATE_TITLE_ONLY = false;

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
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> Fault.run("title.button", () -> {
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
        }));
    }

    /**
     * Các callback gameplay. Mỗi callback và mỗi phần trong callback nằm trong Fault riêng:
     * một phần lỗi thì tắt đúng phần đó, các phần khác vẫn chạy.
     */
    private static void registerGameplayCallbacks() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> Fault.run("tick", () -> onClientTick(client)));

        // Các phần HUD chạy độc lập: một phần lỗi không làm mất các phần còn lại
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (Gate.loading() || MinecraftClient.getInstance().world == null) return;
            Fault.run("hud.island", () -> DynamicIslandHud.render(context));
            Fault.run("hud.elements", () -> ElementsHud.render(context));
            Fault.run("hud.crosshair", () -> CrosshairHud.render(context));
            Fault.run("hud.lyrics", () -> LyricsHud.render(context));
            Fault.run("hud.afk", () -> AfkOverlay.render(context)); // phủ cuối cùng, nằm trên mọi HUD
        });

        // Hiệu ứng trong thế giới (xem Effects)
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            if (!Gate.loading() && MinecraftClient.getInstance().world != null) {
                Fault.run("world.effects", () -> Effects.render(ctx));
            }
        });

        // Bàn phím ảo và thanh nút nhanh trên màn hình chat (tính năng Chat Touch)
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (screen instanceof ChatScreen) Fault.run("chat.keyboard", () -> ChatKeyboard.attach(screen));
        });

        // Đánh trúng thực thể: tạo hiệu ứng tại vị trí mục tiêu (không chặn hành động)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            Fault.run("attack.hitfx", () -> {
                if (ElaraConfig.isOn(Feature.HIT_EFFECT)) HitFx.spawn(entity);
            });
            Fault.run("attack.combat", () -> {
                if (Warmup.ready()) CombatFx.onAttack(player, entity);
            });
            return ActionResult.PASS;
        });

        // Vừa vào server hoặc map: đặt thời gian chờ ngắn, hiệu ứng nặng sẽ bật lại sau đó
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> Fault.run("join.warmup", () -> Warmup.onJoin()));

        // Khi game tắt: ghi đồng bộ cấu hình và nét vẽ skin, để không mất thay đổi cuối cùng
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            Fault.run("stop.config", () -> ElaraConfig.flushNow());
            Fault.run("stop.skin", () -> SkinStore.flushNow());
        });
    }

    /** Mỗi tick. Chưa vào thế giới thì chỉ chạy phần nhẹ. Mỗi bước nằm trong Fault riêng. */
    private static void onClientTick(MinecraftClient client) {
        // Đang vẽ màn hình tải Mojang: không làm gì, để thanh tải chạy hết
        if (Gate.loading()) return;
        // Chưa vào thế giới (sảnh chính): chỉ chạy phần nhẹ, không tick hiệu ứng thế giới
        if (client.world == null) {
            Fault.run("tick.config", () -> ElaraConfig.tick());
            Fault.run("tick.music", () -> MusicPlayer.tick(ElaraConfig.isOn(Feature.MUSIC_PLAYER)));
            Fault.run("tick.menu", () -> handleMenuKey(client));
            while (inspectKey.wasPressed()) { /* phím G chỉ có tác dụng trong thế giới */ }
            return;
        }

        Fault.run("tick.warmup", () -> Warmup.tick());
        Fault.run("tick.culling", () -> CullingManager.tick());
        Fault.run("tick.keys", () -> KeyTracker.tick(client));
        Fault.run("tick.player", () -> PlayerTracker.tick(client));
        Fault.run("tick.motion", () -> PlayerMotion.tick(client));
        Fault.run("tick.buildguide", () -> BuildGuide.tick(client));
        Fault.run("tick.knife", () -> KnifeAnim.tick(client));
        // Tính toán thế giới và bóng: vài tick đầu sau khi vào map thì tạm dừng
        if (Warmup.heavy()) {
            Fault.run("tick.worldfx", () -> WorldFx.tick(client));
            Fault.run("tick.shadow", () -> SoftShadow.tick(client));
        }
        // Cấu hình: chỉ kiểm tra một cờ, ghi sau khoảng 300 ms kể từ thay đổi cuối (xem ElaraConfig)
        Fault.run("tick.config", () -> ElaraConfig.tick());
        // Nhận skin tải về và đẩy texture khi có đổi (việc upload chạy trên luồng này, tức render thread)
        Fault.run("tick.skin", () -> SkinStore.tickMain());
        Fault.run("tick.inspect", () -> {
            while (inspectKey.wasPressed()) KnifeAnim.inspect(client);
        });
        Fault.run("tick.hitfx", () -> HitFx.tick());
        if (Warmup.heavy()) Fault.run("tick.combat", () -> CombatFx.tick(client));
        Fault.run("tick.music", () -> MusicPlayer.tick(ElaraConfig.isOn(Feature.MUSIC_PLAYER)));
        Fault.run("tick.menu", () -> handleMenuKey(client));
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
}
