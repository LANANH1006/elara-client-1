package com.elara.client.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Quản lý gói tài nguyên ngay trong menu Elara: bật/tắt, đổi thứ tự, tải lại. */
public final class PackManager {

    /** Một gói trong danh sách: mã nội bộ, tên hiển thị, đang bật hay không */
    public record Entry(String id, String title, boolean enabled) {}

    private PackManager() {}

    /** Mã các gói đang bật, theo đúng thứ tự game đang áp dụng */
    private static List<String> enabledIds(ResourcePackManager rpm) {
        List<String> ids = new ArrayList<>();
        for (ResourcePackProfile p : rpm.getEnabledProfiles()) {
            ids.add(p.getId());
        }
        return ids;
    }

    /** Gói đang bật theo thứ tự trước, rồi đến các gói đang tắt */
    public static List<Entry> list(MinecraftClient client) {
        ResourcePackManager rpm = client.getResourcePackManager();
        List<String> enabled = enabledIds(rpm);
        List<Entry> out = new ArrayList<>();
        for (String id : enabled) {
            ResourcePackProfile p = rpm.getProfile(id);
            if (p != null) {
                out.add(new Entry(id, p.getDisplayName().getString(), true));
            }
        }
        for (ResourcePackProfile p : rpm.getProfiles()) {
            if (!enabled.contains(p.getId())) {
                out.add(new Entry(p.getId(), p.getDisplayName().getString(), false));
            }
        }
        return out;
    }

    public static void setEnabled(MinecraftClient client, String id, boolean on) {
        ResourcePackManager rpm = client.getResourcePackManager();
        List<String> enabled = enabledIds(rpm);
        if (on) {
            if (!enabled.contains(id)) enabled.add(id);
        } else {
            enabled.remove(id);
        }
        rpm.setEnabledProfiles(enabled);
    }

    /** Đổi chỗ gói với gói liền trước (delta = -1) hoặc liền sau (delta = 1) */
    public static void move(MinecraftClient client, String id, int delta) {
        ResourcePackManager rpm = client.getResourcePackManager();
        List<String> enabled = enabledIds(rpm);
        int i = enabled.indexOf(id);
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
