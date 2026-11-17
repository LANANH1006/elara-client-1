package com.elara.client.skin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.InflaterInputStream;

/**
 * Đọc và ghi PNG bằng java.util.zip, không dùng java.awt hay javax.imageio.
 *
 * Lý do: JRE của PojavLauncher và FCL trên Android có thể thiếu module java.desktop. Mọi lớp chạm tới
 * java.awt hay ImageIO sẽ lỗi khi nạp, nên mod không dùng chúng ở bất kỳ đường nào.
 *
 * Phạm vi: PNG 8-bit với kiểu màu gray, gray+alpha, RGB, RGBA và palette (có tRNS). Không hỗ trợ PNG xen kẽ
 * (interlace) hay độ sâu khác 8-bit. Đủ cho skin Minecraft và ảnh do mod tự ghi.
 */
final class PngCodec {

    /** Ảnh ARGB: mỗi pixel là một int 0xAARRGGBB, xếp theo hàng từ trên xuống */
    record Image(int width, int height, int[] argb) {}

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    /** Chặn kích thước để một file hỏng không đòi hàng trăm MB bộ nhớ */
    private static final int MAX_SIDE = 1024;

    private PngCodec() {}

    static Image decode(byte[] png) throws IOException {
        if (png.length < SIGNATURE.length
                || !Arrays.equals(Arrays.copyOf(png, SIGNATURE.length), SIGNATURE)) {
            throw new IOException("không phải file PNG");
        }
        int width = 0;
        int height = 0;
        int depth = 0;
        int colorType = -1;
        int interlace = 0;
        byte[] palette = null;
        byte[] trns = null;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();

        int pos = SIGNATURE.length;
        while (pos + 8 <= png.length) {
            int len = readInt(png, pos);
            String type = new String(png, pos + 4, 4, StandardCharsets.US_ASCII);
            int data = pos + 8;
            if (len < 0 || len > png.length - data) throw new IOException("PNG hỏng ở khối " + type);
            switch (type) {
                case "IHDR" -> {
                    if (len < 13) throw new IOException("IHDR hỏng");
                    width = readInt(png, data);
                    height = readInt(png, data + 4);
                    depth = png[data + 8] & 0xFF;
                    colorType = png[data + 9] & 0xFF;
                    interlace = png[data + 12] & 0xFF;
                }
                case "PLTE" -> palette = Arrays.copyOfRange(png, data, data + len);
                case "tRNS" -> trns = Arrays.copyOfRange(png, data, data + len);
                case "IDAT" -> idat.write(png, data, len);
                default -> { }
            }
            pos = data + len + 4; // bỏ qua CRC 4 byte
            if (type.equals("IEND")) break;
        }

        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE) {
            throw new IOException("kích thước PNG không hợp lệ");
        }
        if (depth != 8) throw new IOException("chỉ đọc PNG 8-bit, file này " + depth + "-bit");
        if (interlace != 0) throw new IOException("không đọc PNG xen kẽ");
        int channels = switch (colorType) {
            case 0 -> 1;
            case 2 -> 3;
            case 3 -> 1;
            case 4 -> 2;
            case 6 -> 4;
            default -> throw new IOException("kiểu màu PNG không hỗ trợ: " + colorType);
        };
        if (colorType == 3 && palette == null) throw new IOException("PNG palette thiếu PLTE");

        int stride = width * channels;
        int expected = height * (stride + 1);
        byte[] raw;
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(idat.toByteArray()))) {
            raw = in.readNBytes(expected);
        }
        if (raw.length < expected) throw new IOException("dữ liệu ảnh PNG bị thiếu");
        byte[] px = unfilter(raw, stride, height, channels);

        int[] argb = new int[width * height];
        for (int i = 0; i < argb.length; i++) {
            argb[i] = toArgb(colorType, px, i * channels, palette, trns);
        }
        return new Image(width, height, argb);
    }

    /** Bỏ bộ lọc từng hàng (none, sub, up, average, paeth). Trả về dữ liệu pixel thô, không còn byte lọc. */
    private static byte[] unfilter(byte[] raw, int stride, int height, int bpp) throws IOException {
        byte[] out = new byte[stride * height];
        for (int y = 0; y < height; y++) {
            int filter = raw[y * (stride + 1)] & 0xFF;
            int src = y * (stride + 1) + 1;
            int dst = y * stride;
            for (int x = 0; x < stride; x++) {
                int left = x >= bpp ? out[dst + x - bpp] & 0xFF : 0;
                int up = y > 0 ? out[dst - stride + x] & 0xFF : 0;
                int upLeft = (x >= bpp && y > 0) ? out[dst - stride + x - bpp] & 0xFF : 0;
                int v = raw[src + x] & 0xFF;
                switch (filter) {
                    case 0 -> { }
                    case 1 -> v += left;
                    case 2 -> v += up;
                    case 3 -> v += (left + up) >> 1;
                    case 4 -> v += paeth(left, up, upLeft);
                    default -> throw new IOException("bộ lọc PNG không hợp lệ: " + filter);
                }
                out[dst + x] = (byte) v;
            }
        }
        return out;
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        if (pb <= pc) return b;
        return c;
    }

    /** Đổi một pixel từ kiểu màu của file sang ARGB */
    private static int toArgb(int colorType, byte[] px, int o, byte[] palette, byte[] trns) {
        switch (colorType) {
            case 0: {
                int g = px[o] & 0xFF;
                return 0xFF000000 | g << 16 | g << 8 | g;
            }
            case 2:
                return 0xFF000000 | (px[o] & 0xFF) << 16 | (px[o + 1] & 0xFF) << 8 | (px[o + 2] & 0xFF);
            case 3: {
                int idx = px[o] & 0xFF;
                int p = idx * 3;
                if (p + 2 >= palette.length) return 0xFF000000;
                int alpha = (trns != null && idx < trns.length) ? trns[idx] & 0xFF : 0xFF;
                return alpha << 24 | (palette[p] & 0xFF) << 16 | (palette[p + 1] & 0xFF) << 8
                        | (palette[p + 2] & 0xFF);
            }
            case 4: {
                int g = px[o] & 0xFF;
                int a = px[o + 1] & 0xFF;
                return a << 24 | g << 16 | g << 8 | g;
            }
            default:
                return (px[o + 3] & 0xFF) << 24 | (px[o] & 0xFF) << 16 | (px[o + 1] & 0xFF) << 8
                        | (px[o + 2] & 0xFF);
        }
    }

    /** Ghi ảnh ARGB thành PNG RGBA 8-bit, không xen kẽ */
    static byte[] encode(int[] argb, int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || argb.length < width * height) {
            throw new IllegalArgumentException("kích thước ảnh sai");
        }
        int stride = width * 4;
        byte[] raw = new byte[height * (stride + 1)];
        for (int y = 0; y < height; y++) {
            int dst = y * (stride + 1); // raw[dst] = 0: bộ lọc none
            for (int x = 0; x < width; x++) {
                int p = argb[y * width + x];
                int o = dst + 1 + x * 4;
                raw[o] = (byte) (p >> 16);
                raw[o + 1] = (byte) (p >> 8);
                raw[o + 2] = (byte) p;
                raw[o + 3] = (byte) (p >>> 24);
            }
        }
        ByteArrayOutputStream head = new ByteArrayOutputStream(13);
        DataOutputStream hd = new DataOutputStream(head);
        hd.writeInt(width);
        hd.writeInt(height);
        hd.write(8);  // độ sâu bit
        hd.write(6);  // RGBA
        hd.write(0);  // nén deflate
        hd.write(0);  // bộ lọc thích ứng
        hd.write(0);  // không xen kẽ

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        dos.write(SIGNATURE);
        writeChunk(dos, "IHDR", head.toByteArray());
        writeChunk(dos, "IDAT", deflate(raw));
        writeChunk(dos, "IEND", new byte[0]);
        dos.flush();
        return out.toByteArray();
    }

    private static void writeChunk(DataOutputStream dos, String type, byte[] data) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        dos.writeInt(data.length);
        dos.write(name);
        dos.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        dos.writeInt((int) crc.getValue());
    }

    private static byte[] deflate(byte[] data) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 2 + 64);
        byte[] buf = new byte[16384];
        while (!d.finished()) {
            int n = d.deflate(buf);
            out.write(buf, 0, n);
        }
        d.end();
        return out.toByteArray();
    }

    private static int readInt(byte[] b, int p) {
        return ((b[p] & 0xFF) << 24) | ((b[p + 1] & 0xFF) << 16) | ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
    }
}
