package com.elara.client.skin;

import com.elara.client.fx.TexBatch;
import com.elara.client.fx.WorldBatch;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Mô hình nhân vật 3D xem trước trong tab Skin. Các mặt hộp được chiếu phối cảnh bằng CPU rồi vẽ bằng
 * TexBatch (cùng đường với cánh và dao), với texture SkinStore.TEXTURE. Đỉnh được đặt thẳng theo toạ độ
 * GUI, nên mô hình nằm đúng trong khung màn hình. Các mặt được sắp từ xa tới gần (kiểu họa sĩ).
 *
 * Mô hình theo kích thước chuẩn Minecraft: 1 đơn vị = 1/16 block, chân ở y = 0, đỉnh đầu ở y = 32.
 */
public final class SkinPreview {
    /** Khoảng cách camera tới tâm nhân vật, đơn vị mô hình. Lớn hơn thì ít méo phối cảnh. */
    private static final double VIEW_D = 90.0;
    private static final int SIZE = SkinStore.SIZE;

    private static final class Quad {
        final double[] sx = new double[4];
        final double[] sy = new double[4];
        final float[] u = new float[4];
        final float[] v = new float[4];
        double depth;
        double nx;
        double ny;
        double nz;
    }

    private SkinPreview() {}

    /**
     * Vẽ nhân vật trong khung (x, y, w, h) của GUI, xoay quanh trục đứng yawDeg độ.
     * slim = true cho kiểu tay mảnh.
     */
    public static void draw(double x, double y, double w, double h, double yawDeg, boolean slim) {
        if (w < 20 || h < 40) return;
        List<Quad> quads = new ArrayList<>(64);
        double ox = x + w / 2.0;
        double oy = y + h - 6.0;
        double k = (h - 12.0) / 34.0;
        double th = Math.toRadians(yawDeg);
        double cos = Math.cos(th);
        double sin = Math.sin(th);
        double aw = slim ? 3 : 4;
        double armX = slim ? 5.5 : 6.0;

        // Đầu, thân, hai tay, hai chân. Lớp ngoài (grow 0.25) nằm sát bên ngoài lớp trong.
        box(quads, 0, 28, 0, 4, 4, 4, 0, 0, 8, 8, 8, 0.0, cos, sin, k, ox, oy);
        box(quads, 0, 28, 0, 4, 4, 4, 32, 0, 8, 8, 8, 0.25, cos, sin, k, ox, oy);
        box(quads, 0, 18, 0, 4, 6, 2, 16, 16, 8, 12, 4, 0.0, cos, sin, k, ox, oy);
        box(quads, 0, 18, 0, 4, 6, 2, 16, 32, 8, 12, 4, 0.25, cos, sin, k, ox, oy);
        box(quads, -armX, 18, 0, aw / 2, 6, 2, 40, 16, (int) aw, 12, 4, 0.0, cos, sin, k, ox, oy);
        box(quads, -armX, 18, 0, aw / 2, 6, 2, 40, 32, (int) aw, 12, 4, 0.25, cos, sin, k, ox, oy);
        box(quads, armX, 18, 0, aw / 2, 6, 2, 32, 48, (int) aw, 12, 4, 0.0, cos, sin, k, ox, oy);
        box(quads, armX, 18, 0, aw / 2, 6, 2, 48, 48, (int) aw, 12, 4, 0.25, cos, sin, k, ox, oy);
        box(quads, -2, 6, 0, 2, 6, 2, 0, 16, 4, 12, 4, 0.0, cos, sin, k, ox, oy);
        box(quads, -2, 6, 0, 2, 6, 2, 0, 32, 4, 12, 4, 0.25, cos, sin, k, ox, oy);
        box(quads, 2, 6, 0, 2, 6, 2, 16, 48, 4, 12, 4, 0.0, cos, sin, k, ox, oy);
        box(quads, 2, 6, 0, 2, 6, 2, 0, 48, 4, 12, 4, 0.25, cos, sin, k, ox, oy);

        // Xa trước, gần sau (depth lớn hơn là xa hơn)
        quads.sort((a, b) -> Double.compare(b.depth, a.depth));
        TexBatch tb = new TexBatch(new Matrix4f(), 0, 0, 0, SkinStore.TEXTURE);
        for (Quad q : quads) {
            WorldBatch.V n = new WorldBatch.V(q.nx, q.ny, q.nz);
            tb.quadUv(
                    new WorldBatch.V(q.sx[0], q.sy[0], 0), new WorldBatch.V(q.sx[1], q.sy[1], 0),
                    new WorldBatch.V(q.sx[2], q.sy[2], 0), new WorldBatch.V(q.sx[3], q.sy[3], 0),
                    q.u[0], q.v[0], q.u[1], q.v[1], q.u[2], q.v[2], q.u[3], q.v[3],
                    0xFFFFFFFF, n);
        }
        tb.flush();
    }

    /**
     * Một hộp: tâm (cx, cy, cz), nửa kích thước (hx, hy, hz), vị trí texture (u, v) và kích thước texture
     * (tw, th, td). grow nở đều mỗi phía (dùng cho lớp ngoài). Sáu mặt, mỗi mặt có hướng nhìn từ ngoài,
     * trục R sang phải và trục U hướng lên trên của ảnh, khớp với bố cục skin của Minecraft.
     */
    private static void box(List<Quad> out, double cx, double cy, double cz, double hx, double hy, double hz,
                            int u, int v, int tw, int th, int td, double grow,
                            double cos, double sin, double k, double ox, double oy) {
        double ax = hx + grow;
        double ay = hy + grow;
        double az = hz + grow;
        // n: pháp tuyến ra ngoài; r, up: hướng sang phải và lên trên của ảnh; uv: vùng trên texture
        face(out, cx, cy, cz, ax, ay, az, 0, 0, -1, -1, 0, 0, 0, 1, 0, u + td, v + td, u + td + tw, v + td + th,
                cos, sin, k, ox, oy);
        face(out, cx, cy, cz, ax, ay, az, 0, 0, 1, 1, 0, 0, 0, 1, 0, u + 2 * td + tw, v + td, u + 2 * td + 2 * tw,
                v + td + th, cos, sin, k, ox, oy);
        face(out, cx, cy, cz, ax, ay, az, -1, 0, 0, 0, 0, 1, 0, 1, 0, u, v + td, u + td, v + td + th,
                cos, sin, k, ox, oy);
        face(out, cx, cy, cz, ax, ay, az, 1, 0, 0, 0, 0, -1, 0, 1, 0, u + td + tw, v + td, u + 2 * td + tw,
                v + td + th, cos, sin, k, ox, oy);
        face(out, cx, cy, cz, ax, ay, az, 0, 1, 0, -1, 0, 0, 0, 0, 1, u + td, v, u + td + tw, v + td,
                cos, sin, k, ox, oy);
        face(out, cx, cy, cz, ax, ay, az, 0, -1, 0, -1, 0, 0, 0, 0, -1, u + td + tw, v, u + td + 2 * tw, v + td,
                cos, sin, k, ox, oy);
    }

    /**
     * Một mặt. (nx, ny, nz) là pháp tuyến, (rx, ry, rz) là trục phải, (ux, uy, uz) là trục lên trên của ảnh.
     * Chỉ thêm mặt nếu hướng ra ngoài đang quay về phía camera (camera ở phía z âm).
     */
    private static void face(List<Quad> out, double cx, double cy, double cz, double ax, double ay, double az,
                             double nx, double ny, double nz, double rx, double ry, double rz,
                             double ux, double uy, double uz, int u0, int v0, int u1, int v1,
                             double cos, double sin, double k, double ox, double oy) {
        // Tâm mặt và nửa kích thước theo R và U
        double fx = cx + nx * ax;
        double fy = cy + ny * ay;
        double fz = cz + nz * az;
        double hR = Math.abs(rx) * ax + Math.abs(ry) * ay + Math.abs(rz) * az;
        double hU = Math.abs(ux) * ax + Math.abs(uy) * ay + Math.abs(uz) * az;

        // Bốn góc theo thứ tự trên trái, trên phải, dưới phải, dưới trái (nhìn từ ngoài)
        double[][] corners = {
                {fx - rx * hR + ux * hU, fy - ry * hR + uy * hU, fz - rz * hR + uz * hU},
                {fx + rx * hR + ux * hU, fy + ry * hR + uy * hU, fz + rz * hR + uz * hU},
                {fx + rx * hR - ux * hU, fy + ry * hR - uy * hU, fz + rz * hR - uz * hU},
                {fx - rx * hR - ux * hU, fy - ry * hR - uy * hU, fz - rz * hR - uz * hU},
        };
        // Pháp tuyến sau khi xoay quanh trục Y
        double rnx = nx * cos + nz * sin;
        double rnz = -nx * sin + nz * cos;
        if (rnz > 0.05) return;

        Quad q = new Quad();
        double depthSum = 0;
        float[] uu = {u0, u1, u1, u0};
        float[] vv = {v0, v0, v1, v1};
        for (int i = 0; i < 4; i++) {
            double[] c = corners[i];
            double X = c[0] * cos + c[2] * sin;
            double Y = c[1];
            double Z = -c[0] * sin + c[2] * cos;
            double depth = Z + VIEW_D;
            double p = VIEW_D / depth;
            q.sx[i] = ox + X * k * p;
            q.sy[i] = oy - Y * k * p;
            q.u[i] = uu[i] / (float) SIZE;
            q.v[i] = vv[i] / (float) SIZE;
            depthSum += depth;
        }
        q.depth = depthSum / 4.0;
        q.nx = rnx;
        q.ny = ny;
        q.nz = rnz;
        out.add(q);
    }
}
