package com.elara.client.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;

/**
 * Nạp ảnh trực tiếp từ file .jar của mod, không đi qua ResourceManager,
 * nên resource pack bên ngoài không ghi đè được ảnh nền.
 */
public final class ElaraAssets {
    public static final Identifier TITLE_BG = Identifier.of("elaraclient", "runtime/title_bg");

    private static boolean tried = false;

    private ElaraAssets() {}

    /** Gọi trên render thread trước khi vẽ. Chỉ thử nạp một lần. */
    public static void ensureLoaded() {
        if (tried) return;
        tried = true;
        load("assets/elaraclient/textures/gui/title_bg.png", TITLE_BG);
    }

    private static void load(String path, Identifier id) {
        try (InputStream in = ElaraAssets.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[Elara] Không thấy " + path + " trong jar");
                return;
            }
            NativeImage image = NativeImage.read(in);
            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(id, new NativeImageBackedTexture(image));
        } catch (IOException e) {
            System.err.println("[Elara] Không đọc được " + path + ": " + e.getMessage());
        }
    }
}
