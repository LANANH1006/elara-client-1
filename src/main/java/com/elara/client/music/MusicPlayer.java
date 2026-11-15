package com.elara.client.music;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Guard;
import com.elara.client.i18n.Lang;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.openal.AL10;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Trình phát nhạc từ file trong elara/music hoặc từ link http(s) trực tiếp (mp3, wav, ogg).
 *
 * Phát bằng OpenAL, đường âm thanh mà Minecraft đã dùng. Không dùng Clip hay SourceDataLine của
 * javax.sound.sampled: trên PojavLauncher và FCL, máy Android không có mixer Java Sound, nên hai cách đó
 * không mở được và nhạc im lặng. Giải mã chạy ở luồng nền, còn việc đẩy dữ liệu lên OpenAL chạy ở tick,
 * nên khung hình không bị khựng. Mỗi lần chỉ đưa BUFFERS đoạn ngắn CHUNK_S giây vào hàng đợi.
 *
 * YouTube không được hỗ trợ: đó là tải về một dịch vụ có điều khoản cấm, nên mod từ chối các link đó.
 * Lời bài hát đọc từ file .lrc cùng tên. Dữ liệu tần số (8 dải) cho vòng Halo tính bằng Goertzel.
 */
public final class MusicPlayer {

    /** Một dòng lời: mốc thời gian (ms) và nội dung */
    public record Line(long ms, String text) {}

    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?\\](.*)");

    private static final int BUFFERS = 4;
    private static final double CHUNK_S = 0.25;
    private static final int STAGE_BYTES = 65536;
    /** Giới hạn PCM đã giải mã: khoảng 6 phút stereo 44.1 kHz. Quá mức này điện thoại dễ hết RAM. */
    private static final int MAX_PCM_BYTES = 64 * 1024 * 1024;
    private static final int ANALYSIS_FRAMES = 512;
    private static final double[] BAND_HZ = {110, 220, 440, 880, 1760, 3520, 7040, 14080};

    /** Kết quả giải mã, chờ luồng chính lấy ra để phát */
    private record Decoded(byte[] pcm, int channels, int rate, String title, List<Line> lyrics, long token) {}

    private static final class TooLong extends IOException {
        TooLong() { super("too long"); }
    }

    private static final List<Path> TRACKS = new ArrayList<>();
    private static List<Line> lyrics = new ArrayList<>();
    private static String status = "";
    private static String title = "";
    private static int index = -1;
    private static boolean playing;
    /** Đã có PCM và nguồn OpenAL đang giữ bài */
    private static boolean loaded;

    // PCM đang phát
    private static byte[] pcm = new byte[0];
    private static int channels = 2;
    private static int frameBytes = 4;
    private static int rate = 44100;
    private static long totalFrames;
    /** Số frame đã đưa vào hàng đợi OpenAL */
    private static long sentFrames;
    /** Số frame của từng buffer đang xếp hàng, theo thứ tự phát */
    private static final ArrayDeque<Integer> queuedLens = new ArrayDeque<>();
    private static long queuedFrames;

    // OpenAL
    private static boolean alReady;
    private static boolean alBroken;
    private static int source = -1;
    private static final ArrayDeque<Integer> freeBuffers = new ArrayDeque<>();
    private static ByteBuffer stage;

    /** Mức 8 dải tần, 0..1, cập nhật mỗi tick khi đang phát */
    private static final float[] bands = new float[BAND_HZ.length];

    // Giải mã nền
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "elara-music-decode");
        t.setDaemon(true);
        return t;
    });
    private static volatile Decoded ready;
    private static volatile String failure;
    private static volatile boolean decoding;
    /** Tăng mỗi lần chọn bài mới, để kết quả của bài cũ bị bỏ */
    private static volatile long decodeToken = 0;

    private MusicPlayer() {}

    // ---------- thư mục và danh sách ----------

    public static Path dir() {
        Path d = FabricLoader.getInstance().getGameDir().resolve("elara").resolve("music");
        try {
            Files.createDirectories(d);
        } catch (IOException ignored) {
            // Không tạo được thư mục thì danh sách sẽ trống
        }
        return d;
    }

    /** Quét lại thư mục nhạc: wav, mp3, ogg */
    public static void rescan() {
        TRACKS.clear();
        try (Stream<Path> s = Files.list(dir())) {
            s.filter(MusicPlayer::isAudio).sorted().forEach(TRACKS::add);
        } catch (IOException e) {
            status = Lang.get("snd.fail");
        }
    }

    public static List<Path> tracks() { return Collections.unmodifiableList(TRACKS); }
    public static int index() { return index; }
    public static boolean isPlaying() { return playing; }
    public static boolean isLoading() { return decoding; }
    public static String status() { return status; }
    public static String title() { return title; }

    public static String name(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.length() - 4);
    }

    // ---------- chọn bài ----------

    /** Giải mã bài trong thư mục ở luồng nền. Bài đang phát được dừng ngay. */
    public static void play(int i) {
        if (i < 0 || i >= TRACKS.size()) return;
        Path p = TRACKS.get(i);
        beginNewTrack();
        index = i;
        final String shown = name(p);
        title = shown;
        final long token = decodeToken;
        decoding = true;
        status = Lang.get("snd.loading");
        DECODER.execute(() -> {
            try (InputStream in = Files.newInputStream(p)) {
                List<Line> ls = loadLyrics(p);
                publish(token, decode(in, shown, ls, token));
            } catch (Throwable t) {
                Guard.rethrowIfFatal(t);
                fail(token, failureText(t));
            }
        });
    }

    /** Phát link http hoặc https trực tiếp đến file mp3, wav, ogg. YouTube bị từ chối. */
    public static void playUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        ElaraConfig.setMusicUrl(url);
        if (url.isEmpty()) {
            status = Lang.get("snd.empty_url");
            return;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            status = Lang.get("snd.bad_url");
            return;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            status = Lang.get("snd.bad_url");
            return;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.contains("youtube.") || host.equals("youtu.be")) {
            status = Lang.get("snd.yt");
            return;
        }

        beginNewTrack();
        index = -1;
        final String shown = fileNameOf(uri);
        title = shown;
        ElaraConfig.addMusicHistory(url);
        final long token = decodeToken;
        decoding = true;
        status = Lang.get("snd.loading");
        DECODER.execute(() -> {
            try (InputStream in = open(uri.toURL())) {
                publish(token, decode(in, shown, new ArrayList<>(), token));
            } catch (Throwable t) {
                Guard.rethrowIfFatal(t);
                fail(token, failureText(t));
            }
        });
    }

    /** Dừng bài cũ và đổi token, để kết quả đang giải mã của bài cũ bị bỏ */
    private static void beginNewTrack() {
        decodeToken++;
        decoding = false;
        ready = null;
        failure = null;
        clearQueue();
        playing = false;
        loaded = false;
        lyrics = new ArrayList<>();
    }

    private static void publish(long token, Decoded d) {
        if (token == decodeToken) ready = new Decoded(d.pcm(), d.channels(), d.rate(), d.title(), d.lyrics(), token);
    }

    private static void fail(long token, String message) {
        if (token == decodeToken) failure = message;
    }

    private static String failureText(Throwable t) {
        if (t instanceof TooLong) return Lang.get("snd.too_long");
        System.err.println("[Elara] Không phát được nhạc: " + t);
        return Lang.get("snd.fail");
    }

    /** Phát / tạm dừng. Chưa có bài nào thì phát bài đầu tiên của thư mục. */
    public static void toggle() {
        if (!loaded) {
            if (!TRACKS.isEmpty() && !decoding) play(Math.max(0, index));
            return;
        }
        if (playing) pause();
        else resume();
    }

    public static void pause() {
        if (alReady && loaded) AL10.alSourcePause(source);
        playing = false;
    }

    private static void resume() {
        if (!loaded) return;
        if (playedFrames() >= totalFrames) seekFrames(0);
        AL10.alSourcePlay(source);
        playing = true;
    }

    public static void next() {
        if (!TRACKS.isEmpty()) play((index + 1) % TRACKS.size());
    }

    public static void prev() {
        if (!TRACKS.isEmpty()) play(index <= 0 ? TRACKS.size() - 1 : index - 1);
    }

    /** Tua tới vị trí frac (0 đến 1) của bài đang phát */
    public static void seek(double frac) {
        if (!loaded) return;
        seekFrames(Math.round(Math.max(0, Math.min(1, frac)) * totalFrames));
    }

    private static void seekFrames(long target) {
        clearQueue();
        sentFrames = Math.max(0, Math.min(totalFrames, target));
        pump();
        if (playing) AL10.alSourcePlay(source);
    }

    // ---------- âm lượng, tiến trình ----------

    public static int volume() { return ElaraConfig.musicVolume(); }

    public static void setVolume(int percent) {
        ElaraConfig.setMusicVolume(percent);
        if (alReady) AL10.alSourcef(source, AL10.AL_GAIN, ElaraConfig.musicVolume() / 100f);
    }

    public static double progress() {
        return totalFrames <= 0 ? 0 : playedFrames() / (double) totalFrames;
    }

    public static long positionMs() {
        return rate <= 0 ? 0 : playedFrames() * 1000L / rate;
    }

    public static long lengthMs() {
        return rate <= 0 ? 0 : totalFrames * 1000L / rate;
    }

    /** Frame đang nghe: frame đã xong trong các buffer, cộng vị trí trong buffer đang phát */
    private static long playedFrames() {
        if (!loaded || !alReady) return 0;
        int off = AL10.alGetSourcei(source, AL10.AL_SAMPLE_OFFSET);
        return Math.max(0, Math.min(totalFrames, sentFrames - queuedFrames + off));
    }

    // ---------- gọi mỗi tick ----------

    /**
     * Gọi mỗi tick trên luồng chính. Lấy kết quả giải mã, đẩy dữ liệu lên OpenAL, tự chuyển bài khi hết,
     * cập nhật dải tần. Nếu tính năng bị tắt thì tạm dừng.
     */
    public static void tick(boolean enabled) {
        if (!enabled) {
            pause();
            return;
        }
        Decoded d = ready;
        if (d != null) {
            ready = null;
            if (d.token() == decodeToken) start(d);
        }
        String err = failure;
        if (err != null) {
            failure = null;
            decoding = false;
            status = err;
        }
        if (!loaded || !alReady) {
            decay();
            return;
        }
        pump();
        if (playing && queuedLens.isEmpty() && sentFrames >= totalFrames) {
            // Hết bài: với thư mục thì sang bài kế, với link thì dừng
            playing = false;
            if (index >= 0) next();
        }
        updateBands();
    }

    /** Dòng lời đang hát ở mốc thời gian ms, hoặc chuỗi rỗng nếu chưa có */
    public static String lyricAt(long ms) {
        String cur = "";
        for (Line l : lyrics) {
            if (l.ms() <= ms) {
                cur = l.text();
            } else {
                break;
            }
        }
        return cur;
    }

    /** Mốc thời gian bắt đầu của dòng lời hiện tại, dùng để làm mờ dần. -1 nếu chưa có dòng nào. */
    public static long lyricStartMs(long ms) {
        long start = -1;
        for (Line l : lyrics) {
            if (l.ms() <= ms) start = l.ms();
            else break;
        }
        return start;
    }

    /** 8 dải tần, mỗi dải 0..1. Không phát nhạc thì các dải về 0. */
    public static float[] bands() { return bands; }

    // ---------- nội bộ: OpenAL ----------

    /** Khởi tạo nguồn và buffer OpenAL một lần. Trả về false nếu thiết bị âm thanh không mở được. */
    private static boolean ensureAl() {
        if (alBroken) return false;
        if (alReady) return true;
        try {
            source = AL10.alGenSources();
            for (int i = 0; i < BUFFERS; i++) freeBuffers.add(AL10.alGenBuffers());
            stage = ByteBuffer.allocateDirect(STAGE_BYTES).order(ByteOrder.nativeOrder());
            alReady = true;
            return true;
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            alBroken = true;
            System.err.println("[Elara] Không mở được OpenAL cho nhạc: " + t);
            return false;
        }
    }

    private static void start(Decoded d) {
        decoding = false;
        if (!ensureAl()) {
            status = Lang.get("snd.no_audio");
            return;
        }
        clearQueue();
        pcm = d.pcm();
        channels = d.channels();
        rate = d.rate();
        frameBytes = channels * 2;
        totalFrames = pcm.length / frameBytes;
        sentFrames = 0;
        lyrics = d.lyrics();
        title = d.title();
        loaded = totalFrames > 0;
        status = loaded ? "" : Lang.get("snd.fail");
        if (!loaded) return;
        AL10.alSourcef(source, AL10.AL_GAIN, ElaraConfig.musicVolume() / 100f);
        pump();
        AL10.alSourcePlay(source);
        playing = true;
    }

    /** Dừng nguồn và trả mọi buffer về danh sách rảnh */
    private static void clearQueue() {
        if (!alReady) {
            queuedLens.clear();
            queuedFrames = 0;
            return;
        }
        AL10.alSourceStop(source);
        int done = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < done; i++) freeBuffers.add(AL10.alSourceUnqueueBuffers(source));
        queuedLens.clear();
        queuedFrames = 0;
    }

    /** Nạp thêm đoạn PCM vào các buffer rảnh, và phát tiếp nếu nguồn đã dừng vì cạn hàng đợi */
    private static void pump() {
        if (!alReady || !loaded) return;
        int done = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < done; i++) {
            freeBuffers.add(AL10.alSourceUnqueueBuffers(source));
            Integer len = queuedLens.pollFirst();
            if (len != null) queuedFrames -= len;
        }

        int chunkFrames = Math.max(1, Math.min(STAGE_BYTES / frameBytes, (int) Math.round(rate * CHUNK_S)));
        int format = channels == 2 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;
        while (!freeBuffers.isEmpty() && sentFrames < totalFrames) {
            int buf = freeBuffers.pollFirst();
            int frames = (int) Math.min(chunkFrames, totalFrames - sentFrames);
            stage.clear();
            stage.put(pcm, (int) (sentFrames * frameBytes), frames * frameBytes);
            stage.flip();
            AL10.alBufferData(buf, format, stage, rate);
            AL10.alSourceQueueBuffers(source, buf);
            queuedLens.addLast(frames);
            queuedFrames += frames;
            sentFrames += frames;
        }

        if (playing && !queuedLens.isEmpty()
                && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(source);
        }
    }

    // ---------- nội bộ: dải tần ----------

    /** Goertzel tại 8 tần số trên ANALYSIS_FRAMES frame kế từ vị trí đang nghe. Rẻ, chạy được mỗi tick. */
    private static void updateBands() {
        long start = playedFrames();
        int n = (int) Math.min(ANALYSIS_FRAMES, totalFrames - start);
        if (!playing || n < 64) {
            decay();
            return;
        }
        for (int b = 0; b < BAND_HZ.length; b++) {
            double coeff = 2.0 * Math.cos(2.0 * Math.PI * BAND_HZ[b] / rate);
            double s1 = 0;
            double s2 = 0;
            for (int i = 0; i < n; i++) {
                int idx = (int) ((start + i) * frameBytes);
                // Little-endian 16-bit, lấy kênh trái
                double x = ((pcm[idx] & 0xFF) | (pcm[idx + 1] << 8)) / 32768.0;
                double s0 = x + coeff * s1 - s2;
                s2 = s1;
                s1 = s0;
            }
            double power = s1 * s1 + s2 * s2 - coeff * s1 * s2;
            double mag = Math.sqrt(Math.max(0, power)) / (n * 0.5);
            float v = (float) Math.min(1.0, mag * 4.0);
            bands[b] = bands[b] * 0.55f + v * 0.45f;
        }
    }

    private static void decay() {
        for (int b = 0; b < bands.length; b++) bands[b] *= 0.85f;
    }

    // ---------- nội bộ: giải mã và đọc ----------

    /** Giải mã mọi định dạng mà mp3spi và vorbisspi đọc được, đổi về PCM 16-bit little-endian */
    private static Decoded decode(InputStream raw, String name, List<Line> ls, long token) throws IOException {
        try (AudioInputStream src = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
            AudioFormat base = src.getFormat();
            int ch = base.getChannels() >= 2 ? 2 : 1;
            float fr = base.getSampleRate();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fr, 16, ch, ch * 2, fr, false);
            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(target, src)) {
                byte[] data = readCapped(pcmStream);
                return new Decoded(data, ch, Math.round(fr), name, ls, token);
            } catch (IllegalArgumentException e) {
                throw new IOException("không đổi được định dạng", e);
            }
        } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
            throw new IOException("định dạng không hỗ trợ", e);
        }
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > MAX_PCM_BYTES) throw new TooLong();
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static InputStream open(URL u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "ElaraClient");
        int code = c.getResponseCode();
        if (code >= 400) throw new IOException("HTTP " + code);
        return c.getInputStream();
    }

    private static String fileNameOf(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) return uri.getHost() == null ? "link" : uri.getHost();
        String seg = path.substring(path.lastIndexOf('/') + 1);
        return seg.isEmpty() ? (uri.getHost() == null ? "link" : uri.getHost()) : seg;
    }

    private static boolean isAudio(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".wav") || n.endsWith(".mp3") || n.endsWith(".ogg");
    }

    /** Đọc file .lrc cùng tên với bài hát. Dòng nào không đúng định dạng thì bỏ qua. */
    private static List<Line> loadLyrics(Path track) {
        List<Line> out = new ArrayList<>();
        Path lrc = track.resolveSibling(name(track) + ".lrc");
        if (!Files.isRegularFile(lrc)) return out;
        try {
            for (String raw : Files.readAllLines(lrc, StandardCharsets.UTF_8)) {
                Matcher m = LRC.matcher(raw.trim());
                if (!m.matches()) continue;
                long ms = Long.parseLong(m.group(1)) * 60_000L
                        + Long.parseLong(m.group(2)) * 1000L
                        + fraction(m.group(3));
                out.add(new Line(ms, m.group(4).trim()));
            }
        } catch (IOException | NumberFormatException e) {
            return new ArrayList<>();
        }
        out.sort(Comparator.comparingLong(Line::ms));
        return out;
    }

    /** Phần thập phân của giây trong [mm:ss.xx] hoặc [mm:ss.xxx], đổi ra ms */
    private static long fraction(String f) {
        if (f == null || f.isEmpty()) return 0;
        if (f.length() == 1) return Long.parseLong(f) * 100;
        if (f.length() == 2) return Long.parseLong(f) * 10;
        return Long.parseLong(f);
    }
}
