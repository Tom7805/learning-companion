package com.learningcompanion.ai.rag;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tách văn bản thành các tiếng có nghĩa để so câu hỏi với đoạn tài liệu. Tiếng Việt viết theo âm tiết nên từ ghép được
 * so bằng cặp tiếng liền nhau. Các tiếng giữ dấu (để "mạng" khác "mang"); câu hỏi gõ không dấu vẫn khớp nhờ dạng bỏ dấu.
 */
public final class TextTerms {

    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    /** Hư từ và từ để hỏi: có mặt ở hầu hết mọi câu nên không giúp phân biệt đoạn nào liên quan. */
    private static final Set<String> STOPWORDS = Set.of(
            "và", "là", "của", "có", "các", "những", "một", "cho", "với", "trong", "theo", "được", "để", "khi", "thì",
            "mà", "này", "đó", "nào", "gì", "sao", "như", "thế", "về", "từ", "ra", "vào", "lên", "không", "hay",
            "hoặc", "nếu", "bị", "do", "vì", "đến", "tới", "cũng", "đã", "đang", "sẽ", "rất", "nhiều", "ít", "hãy",
            "giúp", "tôi", "em", "mình", "bạn", "ạ", "nhé", "vậy", "bao", "nhiêu", "ở", "đâu", "ai", "cách", "làm",
            "giải", "thích", "trình", "bày", "nêu", "biết", "the", "a", "an", "of", "to", "in", "is", "are",
            "and", "or", "what", "how", "why", "which", "does", "for", "on", "with", "explain");

    private TextTerms() {
    }

    /** Tiếng viết thường, giữ dấu, theo thứ tự xuất hiện; bỏ hư từ và tiếng một ký tự. */
    public static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        for (String token : SPLIT.split(normalized)) {
            if (token.length() > 1 && !STOPWORDS.contains(token)) {
                words.add(token);
            }
        }
        return words;
    }

    /** Dạng bỏ dấu của một tiếng: "đạo" thành "dao". */
    public static String fold(String word) {
        return MARKS.matcher(Normalizer.normalize(word, Normalizer.Form.NFD)).replaceAll("").replace('đ', 'd');
    }

    /** Các cặp tiếng liền nhau của câu (sau khi bỏ hư từ), ví dụ "đạo hàm", "hàm riêng". */
    public static Set<String> pairs(List<String> words) {
        Set<String> pairs = new LinkedHashSet<>();
        for (int i = 0; i + 1 < words.size(); i++) {
            pairs.add(words.get(i) + " " + words.get(i + 1));
        }
        return pairs;
    }

    /** Câu không có dấu nào (người học gõ không dấu): khi đó so theo dạng bỏ dấu. */
    public static boolean unaccented(List<String> words) {
        return words.stream().allMatch(word -> fold(word).equals(word));
    }
}
