package com.elara.client.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;

/**
 * Fix Lag: chỉnh các tùy chọn có sẵn của game theo những tính năng đang bật.
 *
 * Giá trị gốc được lưu vào config trước lần đầu can thiệp. Mỗi lần áp dụng đều tính
 * từ giá trị gốc, nên bật/tắt nhiều lần không bị cộng dồn, và tắt hết thì trả về đúng như cũ.
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
    }

    private static final int RENDER_VIEW_CAP = 12;
    private static final int GPU_VIEW_CAP = 8;
    private static final int GPU_FPS_CAP = 60;
    private static final double ENTITY_DISTANCE_LOW = 0.5;

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

    public static boolean anyEnabled() {
        for (Feature f : Feature.inGroup(Feature.Group.FIX_LAG)) {
            if (ElaraConfig.isOn(f)) return true;
        }
        return false;
    }

    /** Gọi khi bật/tắt một tính năng Fix Lag, và một lần lúc khởi động game */
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
        }
        apply(opts, b);
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
        return b;
    }

    private static void apply(GameOptions o, Backup b) {
        boolean render = ElaraConfig.isOn(Feature.LAG_RENDER);
        boolean entities = ElaraConfig.isOn(Feature.LAG_ENTITIES);
        boolean particles = ElaraConfig.isOn(Feature.LAG_PARTICLES);
        boolean gpu = ElaraConfig.isOn(Feature.LAG_GPU_SAVER);

        int view = b.viewDistance;
        if (render) view = Math.min(view, RENDER_VIEW_CAP);
        if (gpu) view = Math.min(view, GPU_VIEW_CAP);
        o.getViewDistance().setValue(view);

        int fps = gpu ? Math.min(b.maxFps, GPU_FPS_CAP) : b.maxFps;
        o.getMaxFps().setValue(fps);

        var particlesOpt = o.getParticles();
        particlesOpt.setValue(pick(particlesOpt.getValue(), particles ? "MINIMAL" : b.particles));

        o.getEntityShadows().setValue(entities ? Boolean.FALSE : b.entityShadows);
        o.getEntityDistanceScaling().setValue(entities ? ENTITY_DISTANCE_LOW : b.entityDistance);

        o.getGraphicsMode().setValue(gpu
                ? GraphicsMode.FAST
                : GraphicsMode.valueOf(b.graphics));
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

        ElaraConfig.setBackup(null);
        ElaraConfig.save();
    }
}
