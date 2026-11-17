import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Kiểm tra com.elara.client.i18n.Lang chạy được lúc khởi động (không ném lỗi trong <clinit>).
 *
 * Cách chạy từ thư mục gốc của repo:
 *   mkdir -p out
 *   javac -encoding UTF-8 -d out src/main/java/com/elara/client/i18n/Lang.java tools/LangTest.java
 *   java -cp out LangTest
 */
public final class LangTest {
    private static final Pattern ENTRY = Pattern.compile("^\\s*e\\(\"");
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args.length > 0 ? args[0] : "src/main/java/com/elara/client/i18n/Lang.java");

        // 1. Nạp class sẽ chạy <clinit>. Nếu bảng có khóa trùng kiểu Map.ofEntries thì lỗi xảy ra ở đây.
        Class<?> lang;
        try {
            lang = Class.forName("com.elara.client.i18n.Lang");
        } catch (Throwable t) {
            System.out.println("FAIL <clinit> Lang ném lỗi: " + t);
            System.exit(1);
            return;
        }
        check(true, "<clinit> của Lang chạy xong không lỗi");

        // 2. Đếm khóa VI và EN trong bảng TEXT
        Field textField = lang.getDeclaredField("TEXT");
        textField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String[]> text = (Map<String, String[]>) textField.get(null);

        int vi = 0;
        int en = 0;
        boolean shapeOk = true;
        for (Map.Entry<String, String[]> entry : text.entrySet()) {
            String[] pair = entry.getValue();
            if (pair == null || pair.length != 2) {
                shapeOk = false;
                continue;
            }
            if (pair[0] != null && !pair[0].isBlank()) vi++;
            if (pair[1] != null && !pair[1].isBlank()) en++;
        }
        check(shapeOk, "mỗi khóa có đúng một cặp VI/EN");
        check(vi == text.size(), "mọi khóa có chữ VI không rỗng (" + vi + "/" + text.size() + ")");
        check(en == text.size(), "mọi khóa có chữ EN không rỗng (" + en + "/" + text.size() + ")");
        check(vi == en, "số khóa VI = số khóa EN (" + vi + " = " + en + ")");

        // 3. Số mục e("...") trong mã nguồn phải bằng số khóa trong bảng: không mất mục, không còn trùng
        long sourceEntries = Files.readAllLines(source, StandardCharsets.UTF_8).stream()
                .filter(line -> ENTRY.matcher(line).find())
                .count();
        check(sourceEntries == text.size(),
                "số mục trong mã nguồn = số khóa trong bảng (" + sourceEntries + " = " + text.size() + ")");

        // 4. Getter công khai: khóa music.hint đúng nội dung ở cả hai ngôn ngữ
        Method setCode = lang.getMethod("setCode", String.class);
        Method get = lang.getMethod("get", String.class);
        setCode.invoke(null, "vi");
        check("Hỗ trợ .wav .mp3 .ogg. Lời: file .lrc cùng tên".equals(get.invoke(null, "music.hint")),
                "music.hint (VI) đúng nội dung");
        setCode.invoke(null, "en");
        check("Supports .wav .mp3 .ogg. Lyrics: same-name .lrc".equals(get.invoke(null, "music.hint")),
                "music.hint (EN) đúng nội dung");
        setCode.invoke(null, "vi");

        // 5. Bảng TEXT không sửa được sau khi tạo
        try {
            text.put("test.key", new String[] {"a", "b"});
            check(false, "bảng TEXT không sửa được");
        } catch (UnsupportedOperationException expected) {
            check(true, "bảng TEXT không sửa được (unmodifiable)");
        }

        // 6. Helper table(): khóa trùng không ném lỗi, mục sau ghi đè mục trước
        Method table = lang.getDeclaredMethod("table", Map.Entry[].class);
        table.setAccessible(true);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map.Entry[] sample = {
                Map.entry("k", new String[] {"a", "b"}),
                Map.entry("k", new String[] {"c", "d"}),
                Map.entry("x", new String[] {"e", "f"})
        };
        try {
            @SuppressWarnings("unchecked")
            Map<String, String[]> built = (Map<String, String[]>) table.invoke(null, (Object) sample);
            check(built.size() == 2, "table() gộp khóa trùng thành một mục (size=" + built.size() + ")");
            check(built.get("k") == sample[1].getValue(), "table() giữ mục sau khi khóa trùng");
            try {
                built.put("z", new String[] {"a"});
                check(false, "table() trả về bảng không sửa được");
            } catch (UnsupportedOperationException expected) {
                check(true, "table() trả về bảng không sửa được");
            }
        } catch (Throwable t) {
            check(false, "table() với khóa trùng ném lỗi: " + t);
        }

        System.out.println(failures == 0
                ? "KẾT QUẢ: tất cả kiểm tra đều đạt"
                : "KẾT QUẢ: " + failures + " kiểm tra không đạt");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(boolean ok, String name) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failures++;
    }
}
