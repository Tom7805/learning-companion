package com.learningcompanion.shared.export;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Nơi mỗi chức năng ghi phần dữ liệu của mình vào gói xuất. Đường dẫn là tương đối trong thư mục riêng của
 * phần đó, dấu "/" ngăn cách thư mục (ví dụ "files/Toán/bai-1.pdf"). Tên lấy từ dữ liệu người học (tên tài liệu,
 * tên thư mục) phải đi qua {@link #path(String...)} để không tạo thư mục ngoài ý muốn. Trùng tên thì gói tự thêm
 * hậu tố " (2)", " (3)"; mọi hàm ghi trả về đường dẫn thật của tệp trong phần đó để bảng kê trỏ đúng tệp.
 */
public interface ExportWriter {

    String json(String path, Object value);

    /** Bảng tính văn bản: dòng đầu là tên cột. */
    String csv(String path, List<String> headers, List<List<?>> rows);

    String text(String path, String content);

    String bytes(String path, byte[] content);

    /** Ghép các tên thành đường dẫn trong gói, mỗi tên được làm sạch để mở được trên mọi hệ điều hành. */
    static String path(String... segments) {
        return Arrays.stream(segments).map(ExportWriter::segment).collect(Collectors.joining("/"));
    }

    /**
     * Một tên tệp hoặc thư mục an toàn: bỏ ký tự điều khiển và ký tự cấm trên Windows, không cho "." hay ".."
     * và tên thiết bị dành riêng, giới hạn độ dài nhưng giữ phần mở rộng.
     */
    static String segment(String raw) {
        String name = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC);
        name = Names.FORBIDDEN.matcher(name).replaceAll("_").strip();
        name = Names.TRAILING_DOTS.matcher(name).replaceAll("").strip();
        if (name.isEmpty()) {
            return "_";
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        if (Names.RESERVED.contains(base.toUpperCase(Locale.ROOT))) {
            base = "_" + base;
        }
        if (extension.length() > 16) {
            base = base + extension;
            extension = "";
        }
        if (base.length() + extension.length() > Names.MAX_LENGTH) {
            base = base.substring(0, Names.MAX_LENGTH - extension.length()).strip();
        }
        return base + extension;
    }

    /** Hằng số cho việc làm sạch tên. */
    final class Names {
        static final int MAX_LENGTH = 120;
        static final Pattern FORBIDDEN = Pattern.compile("[\\p{Cntrl}<>:\"/\\\\|?*]");
        static final Pattern TRAILING_DOTS = Pattern.compile("\\.+$");
        static final Set<String> RESERVED = Set.of("CON", "PRN", "AUX", "NUL",
                "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

        private Names() {
        }
    }
}
