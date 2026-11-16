package com.elara.client.music;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Giải mã mp3, wav, ogg bằng javax.sound.sampled, rồi đưa về PCM 16-bit little-endian stereo ở tần số đích.
 *
 * Lớp này chỉ được nạp khi bắt đầu phát một bài (MusicPlayer gọi tới), không nằm trong đường khởi động.
 * Nếu máy thiếu java.desktop thì lỗi chỉ xuất hiện lúc phát nhạc, không làm sập game lúc mở.
 */
final class AudioDecoder {

    private AudioDecoder() {}

    /**
     * Giải mã luồng thành stereo ở outRate. Vượt maxBytes thì ném MusicPlayer.TooLong.
     * Mono được nhân đôi sang hai kênh; đổi tần số bằng nội suy tuyến tính.
     */
    static byte[] decodeStereo(InputStream raw, int outRate, int maxBytes) throws IOException {
        try (AudioInputStream src = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
            AudioFormat base = src.getFormat();
            int ch = base.getChannels() >= 2 ? 2 : 1;
            float fr = base.getSampleRate();
            if (fr <= 0) throw new IOException("không rõ tần số lấy mẫu");
            // Giữ tần số gốc ở bước này (JDK không đổi tần số được), tần số đích xử lý ở toStereo
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fr, 16, ch, ch * 2, fr, false);
            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(target, src)) {
                byte[] data = readCapped(pcmStream, maxBytes);
                return toStereo(data, ch, fr, outRate, maxBytes);
            } catch (IllegalArgumentException e) {
                throw new IOException("không đổi được định dạng", e);
            }
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("định dạng không hỗ trợ", e);
        }
    }

    private static byte[] readCapped(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > maxBytes) throw new MusicPlayer.TooLong();
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Đưa PCM 16-bit little-endian mono hoặc stereo về stereo ở outRate */
    private static byte[] toStereo(byte[] src, int ch, float srcRate, int outRate, int maxBytes) throws IOException {
        int srcFrames = src.length / (ch * 2);
        if (srcFrames <= 0) return new byte[0];
        double step = srcRate / outRate;
        long outFrames = (long) ((srcFrames - 1) / step) + 1;
        if (outFrames * 4 > maxBytes) throw new MusicPlayer.TooLong();
        byte[] out = new byte[(int) outFrames * 4];
        for (int i = 0; i < outFrames; i++) {
            double pos = i * step;
            int i0 = (int) pos;
            int i1 = Math.min(i0 + 1, srcFrames - 1);
            float t = (float) (pos - i0);
            for (int c = 0; c < 2; c++) {
                int sc = ch == 1 ? 0 : c;
                int a = pcmSample(src, i0, sc, ch);
                int b = pcmSample(src, i1, sc, ch);
                int v = a + Math.round((b - a) * t);
                int o = i * 4 + c * 2;
                out[o] = (byte) v;
                out[o + 1] = (byte) (v >> 8);
            }
        }
        return out;
    }

    /** Một mẫu 16-bit little-endian tại khung frame, kênh c */
    private static int pcmSample(byte[] src, int frame, int c, int ch) {
        int o = (frame * ch + c) * 2;
        return (short) ((src[o] & 0xFF) | (src[o + 1] << 8));
    }
}
