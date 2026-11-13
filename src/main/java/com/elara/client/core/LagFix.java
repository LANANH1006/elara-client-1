package com.elara.client.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;

/**
 * Fix Lag: chỉnh các tùy chọn có sẵn của game theo những tính năng đang bật.
 *
 * Giá trị gốc được lưu vào config trước lần đầu can thiệp. Mỗi lần áp dụng đều tính
 * từ giá trị gốc, nên bật/tắt nhiều lần không bị cộng dồn, và tắt hết thì trả về đúng như cũ.
 * Thông số của từng tính năng (ngưỡng, tỉ lệ...) đọc từ Layout.extra, chỉnh trong bảng cài đặt.
 */
public final class LagFix {

    /** Giá trị gốc của game. Gson đọc/ghi các trường này nên để public. */
    public static final class Backup {
        public Integer viewDistance;
        public Integer maxFps;
        public String particles;
        public Boolean entityShadows;
        public Double entityDistance;
        public String graphics;
        public String clouds;
        public Integer biome;
        public Integer mipmap;
    }

    private LagFix() {}

    /**
     * Lấy hằng enum có tên {@code name}, cùng kiểu với {@code current}.
     * Không cần import tên kiểu enum của Minecraft, nên không phụ thuộc vào tên package của Yarn.
     */
    private static <E extends Enum<E>> E pick(E current, String name) {
        for (E e : current.getDeclaringClass().getEnumConstants()) {
            if (e.name().equals(name)) return e;
        }
        return current;
    }

    /** Đọc một thông số số của tính năng, có giá trị mặc định */
    private static int num(Feature f, String key, int def) {
        return ElaraConfig.layout(f).get(key, def);
    }

    public static boolean anyEnabled() {
        for (Feature f : Feature.inGroup(Feature.Group.FIX_LAG)) {
            if (ElaraConfig.isOn(f)) return true;
        }
        return false;
    }

    /** Gọi khi bật/tắt một tính năng Fix Lag, khi đổi thông số, khi đổi focus cửa sổ, và lúc khởi động */
    public static void sync(MinecraftClient client) {
        GameOptions opts = client.options;
        if (opts == null) return;

        if (!anyEnabled()) {
            restore(opts);
            return;
        }

        Backup b = ElaraConfig.backup();
        if (b == null) {
            b = capture(opts);
            ElaraConfig.setBackup(b);
        } else {
            fillMissing(opts, b);
        }
        apply(client, opts, b);
        ElaraConfig.save();
    }

    private static Backup capture(GameOptions o) {
        Backup b = new Backup();
        b.viewDistance = o.getViewDistance().getValue();
        b.maxFps = o.getMaxFps().getValue();
        b.particles = o.getParticles().getValue().name();
        b.entityShadows = o.getEntityShadows().getValue();
        b.entityDistance = o.getEntityDistanceScaling().getValue();
        b.graphics = o.getGraphicsMode().getValue().name();
        b.clouds = o.getCloudRenderMode().getValue().name();
        b.biome = o.getBiomeBlendRadius().getValue();
        b.mipmap = o.getMipmapLevels().getValue();
        return b;
    }

    /** Bản lưu từ phiên bản cũ có thể thiếu các mục mới, lấy giá trị hiện tại của game cho phần thiếu */
    private static void fillMissing(GameOptions o, Backup b) {
        if (b.clouds == null) b.clouds = o.getCloudRenderMode().getValue().name();
        if (b.biome == null) b.biome = o.getBiomeBlendRadius().getValue();
        if (b.mipmap == null) b.mipmap = o.getMipmapLevels().getValue();
    }

    private static void apply(MinecraftClient client, GameOptions o, Backup b) {
        boolean render = ElaraConfig.isOn(Feature.LAG_RENDER);
        boolean entities = ElaraConfig.isOn(Feature.LAG_ENTITIES);
        boolean particles = ElaraConfig.isOn(Feature.LAG_PARTICLES);
        boolean gpu = ElaraConfig.isOn(Feature.LAG_GPU_SAVER);
        boolean clouds = ElaraConfig.isOn(Feature.LAG_CLOUDS);
        boolean biome = ElaraConfig.isOn(Feature.LAG_BIOME);
        boolean mip = ElaraConfig.isOn(Feature.LAG_MIPMAP);
        boolean focus = ElaraConfig.isOn(Feature.LAG_FOCUS_FPS);

        int view = b.viewDistance;
        if (render) view = Math.min(view, num(Feature.LAG_RENDER, "viewCap", 12));
        if (gpu) view = Math.min(view, num(Feature.LAG_GPU_SAVER, "viewCap", 8));
        o.getViewDistance().setValue(view);

        int fps = b.maxFps;
        if (gpu) fps = Math.min(fps, num(Feature.LAG_GPU_SAVER, "fpsCap", 60));
        if (focus && !client.isWindowFocused()) fps = Math.min(fps, num(Feature.LAG_FOCUS_FPS, "fps", 10));
        o.getMaxFps().setValue(fps);

        var particlesOpt = o.getParticles();
        particlesOpt.setValue(pick(particlesOpt.getValue(), particles ? "MINIMAL" : b.particles));

        o.getEntityShadows().setValue(entities ? Boolean.FALSE : b.entityShadows);
        o.getEntityDistanceScaling().setValue(entities
                ? num(Feature.LAG_ENTITIES, "pct", 50) / 100.0
                : b.entityDistance);

        o.getGraphicsMode().setValue(gpu
                ? GraphicsMode.FAST
                : GraphicsMode.valueOf(b.graphics));

        var cloudOpt = o.getCloudRenderMode();
        cloudOpt.setValue(pick(cloudOpt.getValue(), clouds ? "OFF" : b.clouds));

        o.getBiomeBlendRadius().setValue(biome ? num(Feature.LAG_BIOME, "radius", 0) : b.biome);
        o.getMipmapLevels().setValue(mip ? num(Feature.LAG_MIPMAP, "levels", 0) : b.mipmap);
    }

    private static void restore(GameOptions o) {
        Backup b = ElaraConfig.backup();
        if (b == null) return;

        o.getViewDistance().setValue(b.viewDistance);
        o.getMaxFps().setValue(b.maxFps);
        var particlesOpt = o.getParticles();
        particlesOpt.setValue(pick(particlesOpt.getValue(), b.particles));
        o.getEntityShadows().setValue(b.entityShadows);
        o.getEntityDistanceScaling().setValue(b.entityDistance);
        o.getGraphicsMode().setValue(GraphicsMode.valueOf(b.graphics));
        var cloudOpt = o.getCloudRenderMode();
        cloudOpt.setValue(pick(cloudOpt.getValue(), b.clouds));
        o.getBiomeBlendRadius().setValue(b.biome);
        o.getMipmapLevels().setValue(b.mipmap);

        ElaraConfig.setBackup(null);
        ElaraConfig.save();
    }
}
