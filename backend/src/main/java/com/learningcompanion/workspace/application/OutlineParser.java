package com.learningcompanion.workspace.application;

import com.learningcompanion.workspace.domain.Topic;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc dàn ý chủ đề mô hình trả về (NCL-06-CN-002) theo định dạng trong lời nhắc của {@code TOPIC_OUTLINE}: mỗi chủ đề
 * một dòng, cấp đánh dấu bằng một tới ba dấu #, tên và mục tiêu học tách bằng " :: ", số nguồn trong ngoặc vuông. Dòng
 * không theo định dạng bị bỏ qua; cấp không bao giờ vượt {@link Topic#MAX_DEPTH} và không nhảy cóc quá một cấp.
 */
final class OutlineParser {

    private static final Pattern HEADING = Pattern.compile("^\\s*(#{1,6})\\s*(.+?)\\s*$");
    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,3}(?:\\s*[,;]\\s*\\d{1,3})*)]");

    private OutlineParser() {
    }

    /** Một dòng dàn ý: cấp (1 là gốc), tên, mục tiêu học và các số nguồn hợp lệ (từ 1 tới {@code sourceCount}). */
    record Line(int depth, String title, String goal, List<Integer> sources) {
    }

    static List<Line> parse(String text, int sourceCount) {
        List<Line> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        int previous = 0;
        for (String raw : text.split("\\R")) {
            Matcher heading = HEADING.matcher(raw);
            if (!heading.matches()) {
                continue;
            }
            String body = heading.group(2);
            Set<Integer> sources = new LinkedHashSet<>();
            Matcher cited = CITATION.matcher(body);
            while (cited.find()) {
                for (String number : cited.group(1).split("[,;]")) {
                    int value = Integer.parseInt(number.trim());
                    if (value >= 1 && value <= sourceCount) {
                        sources.add(value);
                    }
                }
            }
            body = CITATION.matcher(body).replaceAll("").strip();
            int split = body.indexOf("::");
            String title = clean(split < 0 ? body : body.substring(0, split), Topic.TITLE_MAX_LENGTH);
            String goal = split < 0 ? "" : clean(body.substring(split + 2), Topic.GOAL_MAX_LENGTH);
            if (title.isEmpty()) {
                continue;
            }
            int depth = Math.min(Math.min(heading.group(1).length(), Topic.MAX_DEPTH), previous + 1);
            lines.add(new Line(depth, title, goal, List.copyOf(sources)));
            previous = depth;
        }
        return lines;
    }

    /** Khóa so sánh tên chủ đề: không phân biệt hoa thường và khoảng trắng thừa. */
    static String key(String title) {
        return title.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String clean(String value, int max) {
        String cleaned = value.replaceAll("[*_`]+", "").replaceAll("\\s+", " ").strip();
        cleaned = cleaned.replaceAll("[\\s:;,.-]+$", "").strip();
        return cleaned.length() > max ? cleaned.substring(0, max).strip() : cleaned;
    }
}
