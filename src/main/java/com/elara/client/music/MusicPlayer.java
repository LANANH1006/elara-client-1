package com.elara.client.music;

import com.elara.client.core.ElaraConfig;
import com.elara.client.core.Guard;
import com.elara.client.i18n.Lang;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Trình phát nhạc từ file trong elara/music hoặc từ link http(s) trực tiếp (mp3, wav, ogg).
 *
 * Đường phát: Java Sound (SourceDataLine) thử trước; máy không mở được thì dùng OpenAL của Minecraft.
 * javax.sound chỉ được nạp khi bắt đầu phát một bài (xem AudioDecoder và JavaSoundSink), không nằm trong
 * đường khởi động. Giải mã chạy ở luồng nền; việc đẩy dữ liệu lên OpenAL chạy ở tick, nên khung hình không
 * bị khựng. Mỗi lần chỉ đưa BUFFERS đoạn ngắn CHUNK_S giây vào hàng đợi.
 *
 * YouTube không được hỗ trợ: đó là tải về một dịch vụ có điều khoản cấm, nên mod từ chối các link đó.
 * Lời bài hát đọc từ file .lrc cùng tên. Dữ liệu tần số (8 dải) cho vòng Halo tính bằng Goertzel.
 */
public final class MusicPlayer {

    /** Một dòng lời: mốc thời gian (ms) và nội dung */
    public record Line(long ms, String text) {}

    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?\\](.*)");

    private static final int BUFFERS = 4;
    /** Mọi bản nhạc được đưa về trước khi nạp vào OpenAL: PCM 44100 Hz, 16-bit, stereo */
    private static final int OUT_RATE = 44100;
    private static final double CHUNK_S = 0.25;
    private static final int STAGE_BYTES = 65536;
    /** Giới hạn PCM đã giải mã: khoảng 6 phút stereo 44.1 kHz. Quá mức này điện thoại dễ hết RAM. */
    private static final int MAX_PCM_BYTES = 64 * 1024 * 1024;
    /** Giới hạn file tải về (mp3 nén nên lớn hơn PCM ở trên một chút là đủ cho bài dài khoảng 10 phút) */
    private static final long MAX_DOWNLOAD_BYTES = 96L * 1024 * 1024;
    /** Java Sound đứng im quá lâu thì chuyển sang OpenAL */
    private static final long STALL_MS = 4000;
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";
    private static final int ANALYSIS_FRAMES = 512;
    private static final double[] BAND_HZ = {110, 220, 440, 880, 1760, 3520, 7040, 14080};

    /** Kết quả giải mã, chờ luồng chính lấy ra để phát */
    private record Decoded(byte[] pcm, int channels, int rate, String title, List<Line> lyrics, long token) {}

    static final class TooLong extends IOException {
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
    /** Đường phát Java Sound đang dùng (null nếu đang dùng OpenAL) */
    private static JavaSoundSink sink;
    /** Đã thử Java Sound và hỏng trên máy này: từ đó chỉ dùng OpenAL */
    private static boolean sinkBroken;
    private static long lastPlayed = -1;
    private static long lastAdvanceMs;

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
        CompletableFuture
                .supplyAsync(() -> readAndDecodeFile(p, shown, token), DECODER)
                .whenComplete((d, err) -> finish(token, d, err));
    }

    /** Luồng nền: đọc file và giải mã. Lỗi được ném ra để whenComplete ghi nhận. */
    private static Decoded readAndDecodeFile(Path p, String shown, long token) {
        try (InputStream in = Files.newInputStream(p)) {
            List<Line> ls = loadLyrics(p);
            return decode(in, shown, ls, token);
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            throw new CompletionException(t);
        }
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
        // Tải về cache và giải mã ở luồng nền; render thread chỉ nhận kết quả đã chuẩn hóa qua tick()
        CompletableFuture
                .supplyAsync(() -> downloadAndDecode(uri, shown, token), DECODER)
                .whenComplete((d, err) -> finish(token, d, err));
    }

    /** Luồng nền: tải link về file cache rồi giải mã file đó. Lỗi được ném ra để whenComplete ghi nhận. */
    private static Decoded downloadAndDecode(URI uri, String shown, long token) {
        try {
            Path cached = downloadToCache(uri, token);
            try (InputStream in = Files.newInputStream(cached)) {
                return decode(in, shown, new ArrayList<>(), token);
            }
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            throw new CompletionException(t);
        }
    }

    /** Tải link về cache/elara-music/current.audio trong thư mục game, qua file tạm rồi đổi tên */
    private static Path downloadToCache(URI uri, long token) throws IOException {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cache").resolve("elara-music");
        Files.createDirectories(dir);
        Path part = dir.resolve("current.audio.part");
        Path target = dir.resolve("current.audio");
        HttpURLConnection c = connect(uri.toURL());
        try (InputStream in = new BufferedInputStream(c.getInputStream());
             OutputStream out = Files.newOutputStream(part)) {
            copyCapped(in, out, token);
        } finally {
            c.disconnect();
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /** Chép luồng; dừng nếu quá giới hạn, hoặc nếu người chơi đã đổi bài (token khác) */
    private static void copyCapped(InputStream in, OutputStream out, long token) throws IOException {
        byte[] buf = new byte[1 << 16];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            if (token != decodeToken) throw new IOException("bài đã đổi, hủy tải");
            total += n;
            if (total > MAX_DOWNLOAD_BYTES) throw new TooLong();
            out.write(buf, 0, n);
        }
    }

    /** Mở HTTP hoặc HTTPS, tự theo redirect 301, 302, 303, 307, 308 kể cả khi đổi giao thức (tối đa 5 lần) */
    private static HttpURLConnection connect(URL url) throws IOException {
        URL cur = url;
        for (int hop = 0; hop <= 5; hop++) {
            HttpURLConnection c = (HttpURLConnection) cur.openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(20_000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", USER_AGENT);
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("HTTP " + code + " nhưng không có Location");
                cur = new URL(cur, loc);
                continue;
            }
            if (code < 200 || code >= 300) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            return c;
        }
        throw new IOException("quá nhiều lần chuyển hướng");
    }

    /** Nhận kết quả giải mã hoặc lỗi từ luồng nền. Kết quả cũ (token khác) bị bỏ qua trong publish và fail. */
    private static void finish(long token, Decoded d, Throwable err) {
        if (err == null && d != null) {
            publish(token, d);
            return;
        }
        Throwable cause = (err instanceof CompletionException && err.getCause() != null) ? err.getCause() : err;
        fail(token, failureText(cause == null ? new IOException("không rõ lỗi") : cause));
    }

    /** Dừng bài cũ và đổi token, để kết quả đang giải mã của bài cũ bị bỏ */
    private static void beginNewTrack() {
        decodeToken++;
        decoding = false;
        ready = null;
        failure = null;
        closeSink();
        lastPlayed = -1;
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
        String detail = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        return Lang.get("snd.err") + detail;
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
        if (loaded && sink != null) sink.setPaused(true);
        else if (alReady && loaded) AL10.alSourcePause(source);
        playing = false;
    }

    private static void resume() {
        if (!loaded) return;
        if (playedFrames() >= totalFrames) seekFrames(0);
        if (sink != null) sink.setPaused(false);
        else AL10.alSourcePlay(source);
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
        if (sink != null) {
            sink.seek(Math.max(0, Math.min(totalFrames, target)));
            lastPlayed = -1;
            return;
        }
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
        if (sink != null) return sink.playedFrames();
        if (!loaded || !alReady) return 0;
        // AL_SAMPLE_OFFSET thuộc OpenAL 1.1 (AL11). alGetSourcei vẫn nằm ở AL10.
        int off = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
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
        if (!loaded || (sink == null && !alReady)) {
            decay();
            return;
        }
        watchSink();
        pump();
        boolean done = sink != null
                ? sink.finished()
                : (playing && queuedLens.isEmpty() && sentFrames >= totalFrames);
        if (playing && done) {
            // Hết bài: với thư mục thì sang bài kế, với link thì dừng
            playing = false;
            if (sink != null) sink.setPaused(true);
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
        closeSink();
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
        if (!loaded) {
            status = Lang.get("snd.fail");
            return;
        }
        // Ưu tiên Java Sound (không cần OpenAL của Minecraft); không mở được thì dùng OpenAL
        if (!sinkBroken) {
            JavaSoundSink s = openSink();
            if (s != null) {
                sink = s;
                lastPlayed = -1;
                lastAdvanceMs = System.currentTimeMillis();
                playing = true;
                status = Lang.get("snd.playing");
                return;
            }
            sinkBroken = true;
        }
        startAl(0);
    }

    /**
     * Mở Java Sound. Lớp JavaSoundSink chỉ được nạp lúc này, nên nếu máy thiếu java.desktop thì lỗi nạp
     * chỉ làm rơi về OpenAL, không làm sập game.
     */
    private static JavaSoundSink openSink() {
        try {
            return JavaSoundSink.open(pcm, totalFrames, 0);
        } catch (Throwable t) {
            Guard.rethrowIfFatal(t);
            System.err.println("[Elara] Không dùng được Java Sound, chuyển sang OpenAL: " + t);
            return null;
        }
    }

    /** Phát bằng OpenAL từ frame from. Không mở được thiết bị thì báo lỗi và không đánh dấu đang phát. */
    private static void startAl(long from) {
        if (!ensureAl()) {
            loaded = false;
            playing = false;
            status = Lang.get("snd.no_audio");
            return;
        }
        clearQueue();
        sentFrames = Math.max(0, Math.min(totalFrames, from));
        AL10.alSourcef(source, AL10.AL_GAIN, ElaraConfig.musicVolume() / 100f);
        pump();
        AL10.alSourcePlay(source);
        playing = true;
        status = Lang.get("snd.playing");
    }

    private static void closeSink() {
        JavaSoundSink s = sink;
        sink = null;
        if (s != null) s.close();
    }

    /**
     * Java Sound đang nói là đang phát nhưng vị trí không tiến nữa (thiết bị giả hoặc im lặng):
     * đóng nó và phát tiếp bằng OpenAL từ đúng chỗ đang nghe.
     */
    private static void watchSink() {
        if (sink == null) return;
        long now = System.currentTimeMillis();
        long p = sink.playedFrames();
        if (!playing || p != lastPlayed) {
            lastPlayed = p;
            lastAdvanceMs = now;
            return;
        }
        if (p >= totalFrames || now - lastAdvanceMs < STALL_MS) return;
        System.err.println("[Elara] Java Sound không tiến, chuyển sang OpenAL");
        closeSink();
        sinkBroken = true;
        startAl(p);
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
        if (sink != null || !alReady || !loaded) return;
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

    /**
     * Giải mã mọi định dạng mà mp3spi và vorbisspi đọc được, đổi về PCM 16-bit little-endian,
     * rồi chuẩn hóa sang stereo 44100 Hz. Hàm này chạy trên luồng nền, chưa đụng tới OpenAL.
     */
    private static Decoded decode(InputStream raw, String name, List<Line> ls, long token) throws IOException {
        byte[] data = AudioDecoder.decodeStereo(raw, OUT_RATE, MAX_PCM_BYTES);
        return new Decoded(data, 2, OUT_RATE, name, ls, token);
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
