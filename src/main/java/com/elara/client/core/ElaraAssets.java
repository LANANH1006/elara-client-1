package com.elara.client.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;

/**
 * Nạp ảnh trực tiếp từ file .jar của mod, không đi qua ResourceManager,
 * nên resource pack bên ngoài không ghi đè được ảnh nền.
 *
 * Mỗi ảnh được nạp lười: chỉ khi màn hình hoặc hiệu ứng cần tới mới nạp, và chỉ thử một lần.
 * Không có ảnh nào được nạp lúc khởi động. Lỗi nạp không bao giờ ném ra ngoài, chỉ trả về false.
 */
public final class ElaraAssets {
    public static final Identifier TITLE_BG = Identifier.of("elaraclient", "runtime/title_bg");
    /** Texture lông vũ 256x256 cho cánh ảo (RGBA, nền trong suốt) */
    public static final Identifier WING_FEATHER = Identifier.of("elaraclient", "runtime/wings_feather");
    /** Texture tinh vân 256x256 cho cánh kiểu Galaxy (một bên cánh, RGBA, sinh bằng Python) */
    public static final Identifier WING_GALAXY = Identifier.of("elaraclient", "runtime/wings_galaxy");
    /** Vết nứt khiên 64x64 cho Shield Breaker */
    public static final Identifier SHIELD_CRACK = Identifier.of("elaraclient", "runtime/shield_crack");

    private static boolean titleBgTried;
    private static boolean titleBgOk;
    private static boolean featherTried;
    private static boolean featherOk;
    private static boolean galaxyTried;
    private static boolean galaxyOk;
    private static boolean crackTried;
    private static boolean crackOk;

    private ElaraAssets() {}

    /** Gọi trên render thread. Trả về true nếu ảnh nền đã sẵn sàng để vẽ. */
    public static boolean ensureTitleBg() {
        if (!titleBgTried) {
            titleBgTried = true;
            titleBgOk = load("assets/elaraclient/textures/gui/title_bg.png", TITLE_BG);
        }
        return titleBgOk;
    }

    /** Gọi trên render thread. Trả về true nếu texture lông vũ đã sẵn sàng. */
    public static boolean ensureWingFeather() {
        if (!featherTried) {
            featherTried = true;
            featherOk = load("assets/elaraclient/textures/entity/wings_feather.png", WING_FEATHER);
        }
        return featherOk;
    }

    /** Gọi trên render thread. Trả về true nếu texture tinh vân đã sẵn sàng. */
    public static boolean ensureWingGalaxy() {
        if (!galaxyTried) {
            galaxyTried = true;
            galaxyOk = load("assets/elaraclient/textures/entity/wings_galaxy.png", WING_GALAXY);
        }
        return galaxyOk;
    }

    /** Gọi trên render thread. Trả về true nếu vết nứt khiên đã sẵn sàng. */
    public static boolean ensureShieldCrack() {
        if (!crackTried) {
            crackTried = true;
            crackOk = load("assets/elaraclient/textures/gui/shield_crack.png", SHIELD_CRACK);
        }
        return crackOk;
    }

    private static boolean load(String path, Identifier id) {
        try (InputStream in = ElaraAssets.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[Elara] Không thấy " + path + " trong jar");
                return false;
            }
            NativeImage image = NativeImage.read(in);
            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(id, new NativeImageBackedTexture(image));
            return true;
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            System.err.println("[Elara] Không nạp được " + path + ": " + t);
            return false;
        }
    }
}
