package com.elara.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * Gom các tam giác của một lượt vẽ rồi vẽ một lần.
 *
 * Đỉnh chỉ được ghi vào mảng của chính batch. Tessellator chỉ được đụng tới trong flush(), và flush()
 * kết thúc buffer ngay trong cùng một lượt. Vì vậy tại mỗi thời điểm chỉ có tối đa một buffer mở trên
 * Tessellator, và lỗi khi tính hình học không để Tessellator kẹt sang khung sau.
 * Tọa độ truyền vào là tọa độ thế giới, batch tự trừ vị trí camera.
 */
public final class WorldBatch implements AutoCloseable {

    /** Một điểm trong không gian thế giới */
    public record V(double x, double y, double z) {
        public V add(V o) { return new V(x + o.x, y + o.y, z + o.z); }
        public V sub(V o) { return new V(x - o.x, y - o.y, z - o.z); }
        public V mul(double k) { return new V(x * k, y * k, z * k); }
    }

    private final Matrix4f matrix;
    private final double cx;
    private final double cy;
    private final double cz;
    private final boolean additive;
    /** Vị trí đỉnh đã trừ camera, 3 float mỗi đỉnh */
    private float[] pos = new float[3 * 96];
    /** Màu ARGB của từng đỉnh */
    private int[] col = new int[96];
    /** Số đỉnh đã gom */
    private int n;

    public WorldBatch(Matrix4f matrix, double cx, double cy, double cz, boolean additive) {
        this.matrix = matrix;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.additive = additive;
    }

    private void vertex(V p, int argb) {
        if (n == col.length) {
            int cap = col.length * 2;
            pos = Arrays.copyOf(pos, cap * 3);
            col = Arrays.copyOf(col, cap);
        }
        pos[3 * n] = (float) (p.x() - cx);
        pos[3 * n + 1] = (float) (p.y() - cy);
        pos[3 * n + 2] = (float) (p.z() - cz);
        col[n] = argb;
        n++;
    }

    public void tri(V a, V b, V c, int argb) {
        vertex(a, argb);
        vertex(b, argb);
        vertex(c, argb);
    }

    public void quad(V a, V b, V c, V d, int argb) {
        tri(a, b, c, argb);
        tri(a, c, d, argb);
    }

    /** Đoạn thẳng dày {@code w} nằm trên mặt phẳng ngang. */
    public void flatLine(V a, V b, double w, int argb) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return;
        double px = -dz / len * w / 2;
        double pz = dx / len * w / 2;
        V p = new V(px, 0, pz);
        quad(a.add(p), a.sub(p), b.sub(p), b.add(p), argb);
    }

    /** Đoạn thẳng dày {@code w} trong không gian 3D, quay mặt về phía sau. */
    public void beam(V a, V b, double w, int argb) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double dz = b.z() - a.z();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return;
        // perp = dir x (0,1,0), dùng (1,0,0) nếu đoạn gần thẳng đứng
        double px = -dz;
        double pz = dx;
        double pl = Math.sqrt(px * px + pz * pz);
        if (pl < 1e-6) {
            px = 1;
            pz = 0;
            pl = 1;
        }
        V p = new V(px / pl * w / 2, 0, pz / pl * w / 2);
        quad(a.add(p), a.sub(p), b.sub(p), b.add(p), argb);
    }

    /** Vòng nằm ngang quanh tâm {@code c}, bán kính r, dày {@code thick} */
    public void ring(V c, double r, double thick, int segs, double rot, int argb) {
        double ri = r - thick / 2;
        double ro = r + thick / 2;
        for (int i = 0; i < segs; i++) {
            double a0 = rot + 2 * Math.PI * i / segs;
            double a1 = rot + 2 * Math.PI * (i + 1) / segs;
            quad(polar(c, ri, a0), polar(c, ro, a0), polar(c, ro, a1), polar(c, ri, a1), argb);
        }
    }

    private static V polar(V c, double r, double a) {
        return new V(c.x() + r * Math.cos(a), c.y(), c.z() + r * Math.sin(a));
    }

    /** Đổi độ mờ của màu ARGB theo hệ số k từ 0 đến 1 */
    public static int withAlpha(int argb, float k) {
        float kk = Math.max(0f, Math.min(1f, k));
        int alpha = (int) (((argb >>> 24) & 0xFF) * kk);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** Đoạn dày {@code w} luôn quay mặt về camera, nhìn được từ mọi góc */
    public void billboardBeam(V a, V b, V cam, double w, int argb) {
        V dir = b.sub(a);
        V mid = a.add(b).mul(0.5);
        V p = cross(dir, cam.sub(mid));
        double pl = Math.sqrt(p.x() * p.x() + p.y() * p.y() + p.z() * p.z());
        if (pl < 1e-9) {
            beam(a, b, w, argb);
            return;
        }
        V n = p.mul(w / 2 / pl);
        quad(a.add(n), a.sub(n), b.sub(n), b.add(n), argb);
    }

    private static V cross(V a, V b) {
        return new V(a.y() * b.z() - a.z() * b.y(),
                a.z() * b.x() - a.x() * b.z(),
                a.x() * b.y() - a.y() * b.x());
    }

    /**
     * Dựng buffer từ các đỉnh đã gom, vẽ một lần, rồi kết thúc buffer ngay trong lượt này.
     * Không có gì để vẽ thì bỏ qua. Sau lượt này batch trống, có thể gom tiếp.
     */
    public void flush() {
        if (n == 0) return;
        int count = n;
        n = 0;
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        try {
            for (int i = 0; i < count; i++) {
                int argb = col[i];
                b.vertex(matrix, pos[3 * i], pos[3 * i + 1], pos[3 * i + 2])
                        .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
            }
        } catch (RuntimeException e) {
            // Kết thúc buffer dở để Tessellator không kẹt; dữ liệu dở bỏ đi
            b.end();
            throw e;
        }
        var built = b.end();
        try {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            RenderSystem.enableBlend();
            if (additive) {
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            } else {
                RenderSystem.defaultBlendFunc();
            }
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** Đóng batch: vẽ nốt phần đã gom. Dùng với try-with-resources để luôn kết thúc, kể cả khi lỗi. */
    @Override
    public void close() {
        flush();
    }
}
