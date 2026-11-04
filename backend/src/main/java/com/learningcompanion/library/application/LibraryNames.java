package com.learningcompanion.library.application;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Quy tắc tên trong kho tài liệu (NCL-05-CN-001 TC-03): tên được gọn khoảng trắng và chuẩn hóa Unicode, hai tên trùng
 * nhau khi giống nhau không phân biệt hoa thường. Tên trùng được gợi ý thêm số thứ tự, ví dụ "Giải tích (2)".
 */
public final class LibraryNames {

    private static final Pattern NUMBERED = Pattern.compile("^(.*?)\\s*\\((\\d{1,6})\\)$");

    private LibraryNames() {
    }

    /** Tên hiển thị đã gọn: bỏ khoảng trắng thừa, chuẩn hóa NFC để chữ có dấu gõ kiểu nào cũng như nhau. */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
    }

    /** Khóa so trùng của một tên. */
    public static String key(String name) {
        return clean(name).toLowerCase(Locale.ROOT);
    }

    public static <T> Set<String> keys(Collection<T> items, Function<T, String> name) {
        return items.stream().map(name).map(LibraryNames::key).collect(Collectors.toSet());
    }

    /** Tên đầu tiên còn trống theo dạng "Tên (n)", với n từ 2; giữ trong độ dài tối đa. */
    public static String suggest(String name, Set<String> takenKeys, int maxLength) {
        String base = clean(name);
        int next = 2;
        Matcher matcher = NUMBERED.matcher(base);
        if (matcher.matches() && !matcher.group(1).isBlank()) {
            base = matcher.group(1);
            next = Integer.parseInt(matcher.group(2)) + 1;
        }
        for (int n = next; ; n++) {
            String suffix = " (" + n + ")";
            String head = base.length() + suffix.length() > maxLength
                    ? base.substring(0, Math.max(1, maxLength - suffix.length())).strip() : base;
            String candidate = head + suffix;
            if (!takenKeys.contains(key(candidate))) {
                return candidate;
            }
        }
    }
}
