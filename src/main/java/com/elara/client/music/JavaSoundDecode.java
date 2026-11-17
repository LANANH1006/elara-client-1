package com.elara.client.music;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Giải mã mp3 và ogg bằng javax.sound.sampled (kèm SPI jar đóng gói sẵn trong jar).
 * Chỉ dùng cho định dạng không phải WAV. Lớp này chỉ được nạp khi thật sự cần, nên máy thiếu
 * java.desktop chỉ lỗi ở đây, không ảnh hưởng tới WAV.
 */
final class JavaSoundDecode {

    private JavaSoundDecode() {}

    static byte[] decodeStereo(InputStream in, int outRate, int maxBytes) throws IOException {
        try (AudioInputStream src = AudioSystem.getAudioInputStream(in)) {
            AudioFormat base = src.getFormat();
            int ch = base.getChannels() >= 2 ? 2 : 1;
            float fr = base.getSampleRate();
            if (fr <= 0) throw new IOException("không rõ tần số lấy mẫu");
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fr, 16, ch, ch * 2, fr, false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, src)) {
                byte[] data = readCapped(pcm, maxBytes);
                return AudioDecoder.resampleStereo(toStereo16(data, ch), (int) fr, outRate, maxBytes);
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

    /** PCM 16-bit little-endian mono hoặc stereo về stereo 16-bit. Mono được nhân đôi sang hai kênh. */
    private static byte[] toStereo16(byte[] d, int ch) {
        int frames = d.length / (ch * 2);
        byte[] out = new byte[frames * 4];
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < 2; c++) {
                int sc = ch == 1 ? 0 : c;
                int o = (f * ch + sc) * 2;
                int q = (f * 2 + c) * 2;
                out[q] = d[o];
                out[q + 1] = d[o + 1];
            }
        }
        return out;
    }
}
