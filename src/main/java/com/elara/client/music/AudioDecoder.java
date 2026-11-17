package com.elara.client.music;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Giải mã âm thanh về PCM 16-bit little-endian stereo ở tần số đích.
 *
 * WAV được đọc bằng mã Java thuần, không dùng javax.sound, nên chạy được cả trên Android (Pojav, FCL)
 * nơi thiếu module java.desktop. Mp3 và ogg được chuyển cho JavaSoundDecode, lớp đó chỉ được nạp khi cần,
 * nên lỗi thiếu javax.sound chỉ ảnh hưởng đúng định dạng đó, không ảnh hưởng WAV.
 */
final class AudioDecoder {

    private AudioDecoder() {}

    /**
     * Giải mã luồng thành stereo ở outRate. Vượt maxBytes thì ném MusicPlayer.TooLong.
     * Mono được nhân đôi sang hai kênh; đổi tần số bằng nội suy tuyến tính.
     */
    static byte[] decodeStereo(InputStream raw, int outRate, int maxBytes) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw);
        in.mark(12);
        byte[] head = in.readNBytes(12);
        in.reset();
        if (head.length == 12 && ascii(head, 0).equals("RIFF") && ascii(head, 8).equals("WAVE")) {
            // Bỏ header RIFF/WAVE 12 byte: decodeWav phải bắt đầu đọc từ chunk đầu tiên
            in.skipNBytes(12);
            return decodeWav(in, outRate, maxBytes);
        }
        return JavaSoundDecode.decodeStereo(in, outRate, maxBytes);
    }

    /** Đọc chunk WAV: fmt lấy định dạng, data lấy mẫu. Các chunk khác được bỏ qua. */
    private static byte[] decodeWav(InputStream in, int outRate, int maxBytes) throws IOException {
        int tag = -1;
        int channels = 0;
        int rate = 0;
        int bits = 0;
        while (true) {
            byte[] h = in.readNBytes(8);
            if (h.length < 8) throw new IOException("WAV thiếu chunk dữ liệu");
            String id = ascii(h, 0);
            long size = u32(h, 4);
            if (id.equals("fmt ")) {
                if (size < 16 || size > 1024) throw new IOException("chunk fmt không hợp lệ");
                byte[] f = readExact(in, (int) size);
                tag = u16(f, 0);
                channels = u16(f, 2);
                rate = (int) u32(f, 4);
                bits = u16(f, 14);
                // WAVE_FORMAT_EXTENSIBLE: mã định dạng thật nằm ở 2 byte đầu của GUID con (offset 24)
                if (tag == 0xFFFE && size >= 26) tag = u16(f, 24);
                if ((size & 1) == 1) in.skipNBytes(1);
            } else if (id.equals("data")) {
                if (tag < 0) throw new IOException("WAV thiếu chunk fmt trước dữ liệu");
                if (size > maxBytes * 4L) throw new MusicPlayer.TooLong();
                byte[] data = readExact(in, (int) size);
                return convert(data, tag, channels, rate, bits, outRate, maxBytes);
            } else {
                in.skipNBytes(size + (size & 1));
            }
        }
    }

    /** Đổi dữ liệu WAV (PCM nguyên hoặc float 32 bit) về PCM 16-bit stereo, rồi đưa về outRate */
    private static byte[] convert(byte[] d, int tag, int channels, int rate, int bits,
                                  int outRate, int maxBytes) throws IOException {
        if (channels <= 0 || rate <= 0) throw new IOException("WAV có tần số hoặc số kênh không hợp lệ");
        boolean isFloat = tag == 3;
        if (tag != 1 && !isFloat) throw new IOException("WAV nén không hỗ trợ (mã " + tag + ")");
        if (isFloat ? bits != 32 : (bits != 8 && bits != 16 && bits != 24 && bits != 32)) {
            throw new IOException("WAV độ sâu " + bits + " bit không hỗ trợ");
        }
        int bps = bits / 8;
        int frameBytes = channels * bps;
        int frames = d.length / frameBytes;
        byte[] s16 = new byte[frames * 4];
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < 2; c++) {
                int sc = Math.min(c, channels - 1);
                int o = f * frameBytes + sc * bps;
                int v;
                if (isFloat) {
                    float x = Float.intBitsToFloat(s32(d, o));
                    v = Math.round(Math.max(-1f, Math.min(1f, x)) * 32767f);
                } else if (bps == 1) {
                    // WAV 8 bit không dấu, tâm ở 128
                    v = ((d[o] & 0xFF) - 128) << 8;
                } else {
                    // Lấy 2 byte cao nhất của mẫu little-endian (đúng cho 16, 24 và 32 bit)
                    v = (short) ((d[o + bps - 2] & 0xFF) | (d[o + bps - 1] << 8));
                }
                int q = (f * 2 + c) * 2;
                s16[q] = (byte) v;
                s16[q + 1] = (byte) (v >> 8);
            }
        }
        return resampleStereo(s16, rate, outRate, maxBytes);
    }

    /** Đổi tần số PCM 16-bit stereo bằng nội suy tuyến tính. Vượt maxBytes thì ném MusicPlayer.TooLong. */
    static byte[] resampleStereo(byte[] src, int srcRate, int outRate, int maxBytes) throws IOException {
        int srcFrames = src.length / 4;
        if (srcFrames <= 0) return new byte[0];
        double step = (double) srcRate / outRate;
        long outFrames = (long) ((srcFrames - 1) / step) + 1;
        if (outFrames * 4 > maxBytes) throw new MusicPlayer.TooLong();
        byte[] out = new byte[(int) outFrames * 4];
        for (int i = 0; i < outFrames; i++) {
            double pos = i * step;
            int i0 = (int) pos;
            int i1 = Math.min(i0 + 1, srcFrames - 1);
            float t = (float) (pos - i0);
            for (int c = 0; c < 2; c++) {
                int a = sample(src, i0, c);
                int b = sample(src, i1, c);
                int v = a + Math.round((b - a) * t);
                int o = i * 4 + c * 2;
                out[o] = (byte) v;
                out[o + 1] = (byte) (v >> 8);
            }
        }
        return out;
    }

    /** Một mẫu 16-bit little-endian tại khung frame, kênh c của PCM stereo */
    private static int sample(byte[] src, int frame, int c) {
        int o = frame * 4 + c * 2;
        return (short) ((src[o] & 0xFF) | (src[o + 1] << 8));
    }

    private static byte[] readExact(InputStream in, int n) throws IOException {
        byte[] b = in.readNBytes(n);
        if (b.length < n) throw new IOException("WAV bị cắt ngắn");
        return b;
    }

    private static String ascii(byte[] b, int off) {
        return new String(b, off, 4, StandardCharsets.ISO_8859_1);
    }

    private static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] b, int o) {
        return (u16(b, o) & 0xFFFFL) | ((long) u16(b, o + 2) << 16);
    }

    private static int s32(byte[] b, int o) {
        return u16(b, o) | (u16(b, o + 2) << 16);
    }
}
