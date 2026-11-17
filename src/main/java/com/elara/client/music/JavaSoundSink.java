package com.elara.client.music;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Guard;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Đường phát nhạc bằng Java Sound (SourceDataLine, PCM 44100 Hz, 16-bit, stereo).
 * Chạy trên luồng ghi riêng và không đụng tới OpenAL, nên không phụ thuộc OpenAL của Minecraft.
 *
 * open() trả về null nếu máy không có thiết bị Java Sound; khi đó MusicPlayer dùng OpenAL.
 * Âm lượng đọc từ cài đặt mỗi đoạn ghi, nên đổi thanh trượt có tác dụng ngay.
 */
final class JavaSoundSink {
    static final int RATE = 44100;
    private static final int FRAME_BYTES = 4;
    /** Bộ đệm khoảng nửa giây, đủ mượt trên điện thoại */
    private static final int BUFFER_BYTES = RATE / 2 * FRAME_BYTES;
    /** Mỗi lần ghi khoảng 0.1 giây */
    private static final int CHUNK_BYTES = RATE / 10 * FRAME_BYTES;

    private final SourceDataLine line;
    private final byte[] pcm;
    private final long totalFrames;
    private final Object lock = new Object();
    private final byte[] chunk = new byte[CHUNK_BYTES];
    private final Thread writer;
    private volatile boolean closed;
    /** Frame tuyệt đối sẽ ghi tiếp theo vào line. Chỉ đổi khi giữ lock. */
    private long nextFrame;
    private boolean paused;

    private JavaSoundSink(SourceDataLine line, byte[] pcm, long totalFrames, long from) {
        this.line = line;
        this.pcm = pcm;
        this.totalFrames = totalFrames;
        this.nextFrame = Math.max(0, Math.min(totalFrames, from));
        this.writer = new Thread(this::run, "elara-music-sink");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    /** Mở thiết bị và bắt đầu phát từ frame from. Trả về null nếu không mở được. */
    static JavaSoundSink open(byte[] pcm, long totalFrames, long from) {
        SourceDataLine line = null;
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 2, true, false);
            line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
            line.open(fmt, BUFFER_BYTES);
            line.start();
            return new JavaSoundSink(line, pcm, totalFrames, from);
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            if (line != null) {
                try {
                    line.close();
                } catch (RuntimeException ignored) {
                    // đóng lỗi thì bỏ qua, đã có lỗi chính ở trên
                }
            }
            System.err.println("[Elara] Không mở được Java Sound, chuyển sang OpenAL: " + t);
            return null;
        }
    }

    /** Vòng ghi: chỉ ghi khi bộ đệm có chỗ, nên không bao giờ chặn luồng ghi quá lâu */
    private void run() {
        while (!closed) {
            try {
                boolean wrote = false;
                synchronized (lock) {
                    if (!paused && nextFrame < totalFrames && line.available() >= chunk.length) {
                        int frames = (int) Math.min(CHUNK_BYTES / FRAME_BYTES, totalFrames - nextFrame);
                        int n = frames * FRAME_BYTES;
                        System.arraycopy(pcm, (int) (nextFrame * FRAME_BYTES), chunk, 0, n);
                        applyVolume(chunk, n);
                        line.write(chunk, 0, n);
                        nextFrame += frames;
                        wrote = true;
                    }
                }
                if (!wrote) {
                    synchronized (lock) {
                        // Đang tạm dừng hoặc đã ghi hết: chờ có thay đổi, không quay CPU
                        if (!closed && (paused || nextFrame >= totalFrames)) lock.wait(500);
                    }
                    Thread.sleep(8);
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                Guard.rethrowIfFatal(t);
                System.err.println("[Elara] Java Sound dừng do lỗi: " + t);
                closed = true;
                return;
            }
        }
    }

    /** Nhân mẫu 16-bit little-endian với âm lượng hiện tại (0..100%) */
    private static void applyVolume(byte[] b, int n) {
        int pct = ElaraConfig.musicVolume();
        if (pct >= 100) return;
        float g = Math.max(0, pct) / 100f;
        for (int i = 0; i + 1 < n; i += 2) {
            int s = (short) ((b[i] & 0xFF) | (b[i + 1] << 8));
            int v = Math.round(s * g);
            b[i] = (byte) v;
            b[i + 1] = (byte) (v >> 8);
        }
    }

    /** Frame đang nghe: đã ghi trừ phần còn nằm trong bộ đệm của thiết bị */
    long playedFrames() {
        synchronized (lock) {
            int buffered = line.getBufferSize() - line.available();
            long p = nextFrame - buffered / FRAME_BYTES;
            return Math.max(0, Math.min(totalFrames, p));
        }
    }

    /** Đã phát hết: đã ghi hết dữ liệu và bộ đệm thiết bị đã trống */
    boolean finished() {
        synchronized (lock) {
            return nextFrame >= totalFrames && line.available() >= line.getBufferSize();
        }
    }

    void setPaused(boolean p) {
        synchronized (lock) {
            paused = p;
            if (p) line.stop();
            else line.start();
            lock.notifyAll();
        }
    }

    /** Tua tới frame target: xóa phần đang chờ trong bộ đệm rồi ghi từ vị trí mới */
    void seek(long target) {
        synchronized (lock) {
            line.flush();
            nextFrame = Math.max(0, Math.min(totalFrames, target));
        }
    }

    void close() {
        closed = true;
        writer.interrupt();
        synchronized (lock) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (RuntimeException ignored) {
                // thiết bị đã đóng thì không cần làm gì thêm
            }
        }
    }
}
