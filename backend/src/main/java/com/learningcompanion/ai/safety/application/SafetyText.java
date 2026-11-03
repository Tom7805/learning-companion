package com.learningcompanion.ai.safety.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Chuẩn hóa văn bản trước khi so dấu hiệu: dạng Unicode NFC (gõ từ máy Mac ra NFD), chữ thường, gộp khoảng trắng. */
final class SafetyText {

    static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SafetyText() {
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return SPACES.matcher(nfc).replaceAll(" ").strip();
    }

    static Pattern compile(String regex) {
        return Pattern.compile(regex, FLAGS);
    }

    /** Một nhóm từ có ranh giới từ hai bên. */
    static String words(String alternatives) {
        return "\\b(?:" + alternatives + ")\\b";
    }
}
