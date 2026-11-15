package com.elara.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Gom các quad có texture của một lượt vẽ rồi vẽ bằng RenderLayer.getEntityTranslucent,
 * để lông vũ trong suốt đúng ở cả hai mặt. Tọa độ là thế giới, batch tự trừ vị trí camera.
 */
public final class TexBatch {
    private final Matrix4f matrix;
    private final double cx;
    private final double cy;
    private final double cz;
    private final RenderLayer layer;
    private BufferBuilder buf;

    public TexBatch(Matrix4f matrix, double cx, double cy, double cz, Identifier texture) {
        this.matrix = matrix;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.layer = RenderLayer.getEntityTranslucent(texture);
    }

    private BufferBuilder buffer() {
        if (buf == null) {
            buf = Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
        }
        return buf;
    }

    private void vertex(WorldBatch.V p, float u, float v, int argb, WorldBatch.V n) {
        buffer().vertex(matrix, (float) (p.x() - cx), (float) (p.y() - cy), (float) (p.z() - cz))
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal((float) n.x(), (float) n.y(), (float) n.z());
    }

    /** Quad có texture: a = (0, v0), b = (1, v0), c = (1, v1), d = (0, v1). Đỉnh là gốc, v tăng ra chóp. */
    public void quad(WorldBatch.V a, WorldBatch.V b, WorldBatch.V c, WorldBatch.V d,
                     float v0, float v1, int argb, WorldBatch.V n) {
        vertex(a, 0f, v0, argb, n);
        vertex(b, 1f, v0, argb, n);
        vertex(c, 1f, v1, argb, n);
        vertex(d, 0f, v1, argb, n);
    }

    /**
     * Quad có texture, mỗi đỉnh tự cho (u, v). Dùng cho tấm cánh liền khối: các ô lưới có toạ độ riêng.
     * Thứ tự đỉnh a, b, c, d phải đi vòng quanh như quad() để mặt hướng đúng.
     */
    public void quadUv(WorldBatch.V a, WorldBatch.V b, WorldBatch.V c, WorldBatch.V d,
                       float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd,
                       int argb, WorldBatch.V n) {
        vertex(a, ua, va, argb, n);
        vertex(b, ub, vb, argb, n);
        vertex(c, uc, vc, argb, n);
        vertex(d, ud, vd, argb, n);
    }

    /** Vẽ toàn bộ quad đã gom. Không có gì để vẽ thì bỏ qua. */
    public void flush() {
        if (buf == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        layer.draw(buf.end());
        RenderSystem.enableCull();
        buf = null;
    }
}
