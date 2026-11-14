package com.elara.client.music;

import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Trình phát nhạc đọc file .wav trong thư mục elara/music (cạnh mods).
 * Lời bài hát đọc từ file .lrc cùng tên. mp3/ogg cần thêm bộ giải mã nên chưa hỗ trợ.
 */
public final class MusicPlayer {

    /** Một dòng lời: mốc thời gian (ms) và nội dung */
    public record Line(long ms, String text) {}

    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?\\](.*)");

    private static final List<Path> TRACKS = new ArrayList<>();
    private static List<Line> lyrics = new ArrayList<>();
    private static Clip clip;
    private static int index = -1;
    private static boolean playing;
    private static String status = "";

    private MusicPlayer() {}

    public static Path dir() {
        Path d = FabricLoader.getInstance().getGameDir().resolve("elara").resolve("music");
        try {
            Files.createDirectories(d);
        } catch (IOException ignored) {
            // Không tạo được thư mục thì danh sách sẽ trống
        }
        return d;
    }

    /** Quét lại thư mục nhạc, chỉ nhận file .wav */
    public static void rescan() {
        TRACKS.clear();
        try (Stream<Path> s = Files.list(dir())) {
            s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav"))
                    .sorted()
                    .forEach(TRACKS::add);
            status = "";
        } catch (IOException e) {
            status = "Không đọc được thư mục nhạc";
        }
    }

    public static List<Path> tracks() { return Collections.unmodifiableList(TRACKS); }
    public static int index() { return index; }
    public static boolean isPlaying() { return playing; }
    public static String status() { return status; }

    public static String name(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.length() - 4);
    }

    public static void play(int i) {
        stopClip();
        if (i < 0 || i >= TRACKS.size()) return;
        Path p = TRACKS.get(i);
        try (AudioInputStream in = AudioSystem.getAudioInputStream(p.toFile())) {
            Clip c = AudioSystem.getClip();
            c.open(in);
            clip = c;
            index = i;
            lyrics = loadLyrics(p);
            c.start();
            playing = true;
            status = "";
        } catch (Exception e) {
            index = -1;
            status = "Không phát được: " + name(p);
        }
    }

    /** Phát / tạm dừng. Chưa có bài nào thì phát bài đầu tiên. */
    public static void toggle() {
        if (clip == null) {
            if (!TRACKS.isEmpty()) play(Math.max(0, index));
            return;
        }
        if (playing) {
            pause();
        } else {
            if (clip.getFramePosition() >= clip.getFrameLength()) clip.setFramePosition(0);
            clip.start();
            playing = true;
        }
    }

    public static void pause() {
        if (clip != null && playing) {
            clip.stop();
        }
        playing = false;
    }

    public static void next() {
        if (!TRACKS.isEmpty()) play((index + 1) % TRACKS.size());
    }

    public static void prev() {
        if (!TRACKS.isEmpty()) play(index <= 0 ? TRACKS.size() - 1 : index - 1);
    }

    /** Tua tới vị trí frac (0 đến 1) của bài đang phát */
    public static void seek(double frac) {
        if (clip == null || clip.getFrameLength() <= 0) return;
        long frames = clip.getFrameLength();
        long target = Math.round(Math.max(0, Math.min(1, frac)) * frames);
        clip.setFramePosition((int) Math.min(frames - 1, target));
    }

    public static double progress() {
        if (clip == null || clip.getFrameLength() <= 0) return 0;
        return clip.getFramePosition() / (double) clip.getFrameLength();
    }

    public static long positionMs() {
        return clip == null ? 0 : Math.max(0, clip.getMicrosecondPosition() / 1000L);
    }

    public static long lengthMs() {
        return clip == null ? 0 : Math.max(0, clip.getMicrosecondLength() / 1000L);
    }

    /**
     * Gọi mỗi tick. Tự chuyển bài khi hết bài. Nếu tính năng bị tắt thì tạm dừng.
     */
    public static void tick(boolean enabled) {
        if (!enabled) {
            pause();
            return;
        }
        if (clip != null && playing && !clip.isRunning()
                && clip.getFrameLength() > 0 && clip.getFramePosition() >= clip.getFrameLength()) {
            next();
        }
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

    private static void stopClip() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
        playing = false;
        lyrics = new ArrayList<>();
    }

    /** Đọc file .lrc cùng tên với bài hát. Dòng nào không đúng định dạng thì bỏ qua. */
    private static List<Line> loadLyrics(Path wav) {
        List<Line> out = new ArrayList<>();
        Path lrc = wav.resolveSibling(name(wav) + ".lrc");
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
