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

/**
 * Gom các tam giác của một lượt vẽ vào một buffer rồi vẽ một lần.
 * Tọa độ truyền vào là tọa độ thế giới, batch tự trừ vị trí camera.
 */
public final class WorldBatch {

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
    private BufferBuilder buf;

    public WorldBatch(Matrix4f matrix, double cx, double cy, double cz, boolean additive) {
        this.matrix = matrix;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.additive = additive;
    }

    private BufferBuilder buffer() {
        if (buf == null) {
            buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        }
        return buf;
    }

    private void vertex(V p, int argb) {
        buffer().vertex(matrix, (float) (p.x() - cx), (float) (p.y() - cy), (float) (p.z() - cz))
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
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

    /** Vẽ toàn bộ tam giác đã gom. Không có gì để vẽ thì bỏ qua. */
    public void flush() {
        if (buf == null) return;
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        buf = null;
    }
}
