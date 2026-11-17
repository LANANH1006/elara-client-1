package com.elara.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * Gom các quad có texture của một lượt vẽ rồi vẽ bằng RenderLayer.getEntityTranslucent,
 * để lông vũ trong suốt đúng ở cả hai mặt. Tọa độ là thế giới, batch tự trừ vị trí camera.
 *
 * Giống WorldBatch: đỉnh được ghi vào mảng riêng, Tessellator chỉ được dùng trong flush().
 */
public final class TexBatch implements AutoCloseable {
    private final Matrix4f matrix;
    private final double cx;
    private final double cy;
    private final double cz;
    private final RenderLayer layer;
    private float[] pos = new float[3 * 96];
    private float[] uv = new float[2 * 96];
    private float[] nrm = new float[3 * 96];
    private int[] col = new int[96];
    private int n;

    public TexBatch(Matrix4f matrix, double cx, double cy, double cz, Identifier texture) {
        this.matrix = matrix;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.layer = RenderLayer.getEntityTranslucent(texture);
    }

    private void vertex(WorldBatch.V p, float u, float v, int argb, WorldBatch.V nn) {
        if (n == col.length) {
            int cap = col.length * 2;
            pos = Arrays.copyOf(pos, cap * 3);
            uv = Arrays.copyOf(uv, cap * 2);
            nrm = Arrays.copyOf(nrm, cap * 3);
            col = Arrays.copyOf(col, cap);
        }
        int i = n;
        pos[3 * i] = (float) (p.x() - cx);
        pos[3 * i + 1] = (float) (p.y() - cy);
        pos[3 * i + 2] = (float) (p.z() - cz);
        uv[2 * i] = u;
        uv[2 * i + 1] = v;
        nrm[3 * i] = (float) nn.x();
        nrm[3 * i + 1] = (float) nn.y();
        nrm[3 * i + 2] = (float) nn.z();
        col[i] = argb;
        n++;
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

    /** Dựng buffer từ các quad đã gom, vẽ một lần, rồi kết thúc buffer. Không có gì để vẽ thì bỏ qua. */
    public void flush() {
        if (n == 0) return;
        int count = n;
        n = 0;
        BufferBuilder b = Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
        try {
            for (int i = 0; i < count; i++) {
                int argb = col[i];
                b.vertex(matrix, pos[3 * i], pos[3 * i + 1], pos[3 * i + 2])
                        .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                        .texture(uv[2 * i], uv[2 * i + 1])
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                        .normal(nrm[3 * i], nrm[3 * i + 1], nrm[3 * i + 2]);
            }
        } catch (RuntimeException e) {
            // Kết thúc buffer dở để Tessellator không kẹt; dữ liệu dở bỏ đi
            b.end();
            throw e;
        }
        var built = b.end();
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            layer.draw(built);
        } finally {
            RenderSystem.enableCull();
        }
    }

    /** Đóng batch: vẽ nốt phần đã gom. Dùng với try-with-resources để luôn kết thúc, kể cả khi lỗi. */
    @Override
    public void close() {
        flush();
    }
}
