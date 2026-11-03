package com.learningcompanion.shared.audit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Lưới chặn cuối trước khi một dòng nhật ký được lưu (QTN-06, NCL-03-CN-001 TC-04): bỏ mọi trường có thể chứa nội
 * dung do người học nhập, kể cả nằm sâu trong chi tiết, và ghi lại tên các trường đã bỏ (không ghi giá trị) để biết
 * có gì đã bị loại. Chức năng gọi bộ ghi vốn chỉ nên gửi mã và trạng thái; đây là chốt chặn khi ai đó lỡ tay.
 */
public final class ContentFieldStripper {

    /** Khóa chứa tên các trường đã bị bỏ. */
    public static final String REMOVED_FIELDS = "removedFields";

    /**
     * Từ có thể mang nội dung học tập hay bí mật. Một trường bị bỏ khi có một từ trong tên trùng danh sách này, nên
     * {@code note_text}, {@code answerBody} hay {@code userPrompt} đều bị bỏ, còn {@code context} thì không.
     */
    private static final Set<String> CONTENT_WORDS = Set.of(
            "content", "text", "body", "message", "note", "notes", "title", "question", "answer", "prompt",
            "response", "reply", "transcript", "summary", "description", "comment", "html", "markdown", "password",
            "token", "secret", "apikey");

    /** Tách tên trường thành từ theo gạch dưới, gạch ngang, dấu chấm và chỗ đổi chữ thường sang hoa. */
    private static final Pattern WORD_BOUNDARY = Pattern.compile("[_\\-.\\s]+|(?<=[a-z0-9])(?=[A-Z])");

    private ContentFieldStripper() {
    }

    public static Map<String, Object> strip(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        Set<String> removed = new TreeSet<>();
        Map<String, Object> clean = stripMap(details, "", removed);
        if (!removed.isEmpty()) {
            clean.put(REMOVED_FIELDS, List.copyOf(removed));
        }
        return clean;
    }

    private static Map<String, Object> stripMap(Map<?, ?> source, String path, Set<String> removed) {
        Map<String, Object> clean = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String name = String.valueOf(key);
            String fullName = path.isEmpty() ? name : path + "." + name;
            if (isContentKey(name)) {
                removed.add(fullName);
            } else {
                clean.put(name, stripValue(value, fullName, removed));
            }
        });
        return clean;
    }

    private static Object stripValue(Object value, String path, Set<String> removed) {
        if (value instanceof Map<?, ?> map) {
            return stripMap(map, path, removed);
        }
        if (value instanceof Collection<?> items) {
            List<Object> clean = new ArrayList<>(items.size());
            for (Object item : items) {
                clean.add(stripValue(item, path + "[]", removed));
            }
            return clean;
        }
        return value;
    }

    static boolean isContentKey(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (CONTENT_WORDS.contains(lower.replaceAll("[_\\-.\\s]", ""))) {
            return true;
        }
        for (String word : WORD_BOUNDARY.split(name)) {
            if (CONTENT_WORDS.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
