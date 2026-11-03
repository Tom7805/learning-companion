package com.learningcompanion.ai.config.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tìm dữ liệu cá nhân trong lời nhắc hệ thống trước khi lưu (NCL-04-CN-002 TC-03): địa chỉ thư điện tử và số điện
 * thoại. Lời nhắc được gửi tới nhà cung cấp trong mọi lời gọi, nên không được mang thông tin của một người cụ thể.
 * Vị trí trả về tính theo đơn vị UTF-16, khớp với chuỗi trong trình duyệt để giao diện tô đúng đoạn.
 */
public final class PromptPiiDetector {

    public enum Kind { EMAIL, PHONE }

    /** Một đoạn chứa dữ liệu cá nhân: từ {@code start} (gồm) tới {@code end} (không gồm). */
    public record Finding(Kind kind, int start, int end) {
    }

    private static final Pattern EMAIL = Pattern.compile(
            "(?<![\\p{L}\\p{N}._%+-])[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)*\\.\\p{L}{2,}");

    /**
     * Số điện thoại Việt Nam (di động 0 hoặc +84 rồi chín chữ số như 0912 345 678, +84 912.345.678; số bàn có mã vùng
     * như (028) 3829 5555) và số quốc tế bắt đầu bằng dấu cộng. Chữ số có thể cách nhau bằng dấu cách, chấm, gạch
     * ngang hay nằm trong ngoặc. Năm, số trang hay giới hạn độ dài như 2026 hoặc 32000 quá ngắn nên không bị bắt nhầm.
     */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\p{N}+])(?:(?:\\+|00)84[\\s.\\-]?\\(?0?\\)?[\\s.\\-]?|\\(?0)[1-9]"
                    + "(?:[\\s.\\-()]{0,2}\\p{N}){8,9}(?!\\p{N})"
                    + "|(?<![\\p{N}+])\\+[1-9](?:[\\s.\\-()]{0,2}\\p{N}){7,13}(?!\\p{N})");

    private PromptPiiDetector() {
    }

    public static List<Finding> scan(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        collect(EMAIL, Kind.EMAIL, text, findings);
        collect(PHONE, Kind.PHONE, text, findings);
        findings.sort(Comparator.comparingInt(Finding::start));
        // Một đoạn đã thuộc địa chỉ thư thì không tính thêm là số điện thoại.
        List<Finding> distinct = new ArrayList<>();
        for (Finding finding : findings) {
            if (distinct.isEmpty() || finding.start() >= distinct.getLast().end()) {
                distinct.add(finding);
            }
        }
        return List.copyOf(distinct);
    }

    private static void collect(Pattern pattern, Kind kind, String text, List<Finding> into) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            into.add(new Finding(kind, matcher.start(), matcher.end()));
        }
    }
}
