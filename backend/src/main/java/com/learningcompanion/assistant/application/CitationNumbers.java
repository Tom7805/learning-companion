package com.learningcompanion.assistant.application;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Các số trích dẫn xuất hiện trong câu trả lời: [1], [2, 3], [1][2]. Số ngoài khoảng nguồn đã gửi bị bỏ qua. */
final class CitationNumbers {

    private static final Pattern GROUP = Pattern.compile("\\[(\\d{1,2}(?:\\s*[,;]\\s*\\d{1,2})*)]");

    private CitationNumbers() {
    }

    static Set<Integer> in(String text, int sources) {
        Set<Integer> numbers = new TreeSet<>();
        Matcher matcher = GROUP.matcher(text == null ? "" : text);
        while (matcher.find()) {
            for (String part : matcher.group(1).split("[,;]")) {
                int number = Integer.parseInt(part.strip());
                if (number >= 1 && number <= sources) {
                    numbers.add(number);
                }
            }
        }
        return numbers;
    }
}
