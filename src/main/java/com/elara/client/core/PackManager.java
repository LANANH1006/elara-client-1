package com.elara.client.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Quản lý gói tài nguyên ngay trong menu Elara: bật/tắt, đổi thứ tự, tải lại. */
public final class PackManager {

    /** Một gói trong danh sách: tên nội bộ, tên hiển thị, đang bật hay không */
    public record Entry(String name, String title, boolean enabled) {}

    private PackManager() {}

    /** Gói đang bật theo thứ tự trong danh sách trước, rồi đến các gói đang tắt */
    public static List<Entry> list(MinecraftClient client) {
        ResourcePackManager rpm = client.getResourcePackManager();
        List<String> enabled = new ArrayList<>(rpm.getEnabledNames());
        List<Entry> out = new ArrayList<>();
        for (String name : enabled) {
            out.add(new Entry(name, titleOf(rpm, name), true));
        }
        for (ResourcePackProfile p : rpm.getProfiles()) {
            if (!enabled.contains(p.getName())) {
                out.add(new Entry(p.getName(), p.getDisplayName().getString(), false));
            }
        }
        return out;
    }

    private static String titleOf(ResourcePackManager rpm, String name) {
        ResourcePackProfile p = rpm.getProfile(name);
        return p == null ? name : p.getDisplayName().getString();
    }

    public static void setEnabled(MinecraftClient client, String name, boolean on) {
        ResourcePackManager rpm = client.getResourcePackManager();
        List<String> enabled = new ArrayList<>(rpm.getEnabledNames());
        if (on) {
            if (!enabled.contains(name)) enabled.add(name);
        } else {
            enabled.remove(name);
        }
        rpm.setEnabledProfiles(enabled);
    }

    /** Đổi chỗ gói với gói liền trước (delta = -1) hoặc liền sau (delta = 1) */
    public static void move(MinecraftClient client, String name, int delta) {
        ResourcePackManager rpm = client.getResourcePackManager();
        List<String> enabled = new ArrayList<>(rpm.getEnabledNames());
        int i = enabled.indexOf(name);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= enabled.size()) return;
        Collections.swap(enabled, i, j);
        rpm.setEnabledProfiles(enabled);
    }

    /** Tải lại tài nguyên để các thay đổi có hiệu lực */
    public static void reload(MinecraftClient client) {
        client.reloadResources();
    }
}
