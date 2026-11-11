package com.learningcompanion.assistant.application;

import com.learningcompanion.ai.rag.TextTerms;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phần liên quan của đoạn gốc (NCL-07-CN-002): câu của đoạn trích khớp nhiều tiếng nhất với câu trả lời chứa số trích
 * dẫn đó. Giao diện tô sáng phần này trong khung xem nhanh.
 */
public final class CitationHighlighter {

    private static final Pattern SENTENCE = Pattern.compile("[^.!?\\n]+[.!?]?");

    private CitationHighlighter() {
    }

    /** Khoảng [đầu, cuối) trong {@code excerpt}; không tìm được câu nào khớp thì trả về {@code null}. */
    public static Range highlight(String answer, int number, String excerpt) {
        if (answer == null || excerpt == null || excerpt.isBlank()) {
            return null;
        }
        List<String> claimed = TextTerms.words(sentenceCiting(answer, number));
        Set<String> claim = new HashSet<>(claimed);
        Set<String> claimPairs = TextTerms.pairs(claimed);
        if (claim.isEmpty()) {
            return null;
        }
        Range best = null;
        long bestScore = 0;
        Matcher matcher = SENTENCE.matcher(excerpt);
        while (matcher.find()) {
            List<String> words = TextTerms.words(matcher.group());
            // Số tiếng trùng là chính; số cặp tiếng liền nhau trùng phân định khi hai câu ngang nhau.
            long shared = words.stream().distinct().filter(claim::contains).count();
            long score = shared * 100 + TextTerms.pairs(words).stream().filter(claimPairs::contains).count();
            if (score > bestScore) {
                bestScore = score;
                int start = matcher.start();
                while (start < matcher.end() && Character.isWhitespace(excerpt.charAt(start))) {
                    start++;
                }
                best = new Range(start, matcher.end());
            }
        }
        return bestScore >= 200 ? best : null;
    }

    /** Câu của câu trả lời có chứa số trích dẫn {@code [number]} (cả dạng [1, 2]); không có thì cả câu trả lời. */
    static String sentenceCiting(String answer, int number) {
        Pattern marker = Pattern.compile("\\[(?:\\d{1,2}\\s*[,;]\\s*)*" + number + "(?:\\s*[,;]\\s*\\d{1,2})*]");
        Matcher found = marker.matcher(answer);
        if (!found.find()) {
            return answer;
        }
        int end = found.start();
        int start = end;
        while (start > 0 && ".!?\n".indexOf(answer.charAt(start - 1)) < 0) {
            start--;
        }
        // Dấu chấm ngay trước số trích dẫn ("…tăng dần. [1]") thuộc về câu trước đó.
        if (answer.substring(start, end).isBlank() && start > 0) {
            int previous = start - 1;
            while (previous > 0 && ".!?\n".indexOf(answer.charAt(previous - 1)) < 0) {
                previous--;
            }
            start = previous;
        }
        return answer.substring(start, end);
    }

    public record Range(int start, int end) {
    }
}
