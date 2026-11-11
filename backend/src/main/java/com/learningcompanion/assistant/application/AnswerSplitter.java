package com.learningcompanion.assistant.application;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Tách câu trả lời theo luồng thành phần hiện cho người học và khối "Câu hỏi gợi ý:" ở cuối (lời nhắc của trợ lý yêu cầu
 * khối này). Dòng chưa xong được giữ lại tới khi biết nó có phải dòng mở khối gợi ý hay không, nên khối gợi ý không bao
 * giờ lọt vào phần hiện dần.
 */
final class AnswerSplitter {

    private static final Pattern MARKER = Pattern.compile(
            "^\\s*\\**\\s*(câu hỏi gợi ý|gợi ý câu hỏi|suggested questions)\\s*\\**\\s*:?\\s*\\**\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern BULLET = Pattern.compile("^\\s*(?:[-*•]|\\d+[.)])\\s*");

    private final Consumer<String> visible;
    private final StringBuilder shown = new StringBuilder();
    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder tail = new StringBuilder();
    private boolean inSuggestions;

    AnswerSplitter(Consumer<String> visible) {
        this.visible = visible;
    }

    void accept(String text) {
        if (inSuggestions) {
            tail.append(text);
            return;
        }
        pending.append(text);
        int newline;
        while (!inSuggestions && (newline = pending.indexOf("\n")) >= 0) {
            String line = pending.substring(0, newline + 1);
            pending.delete(0, newline + 1);
            if (MARKER.matcher(line.strip()).matches()) {
                inSuggestions = true;
                tail.append(pending);
                pending.setLength(0);
            } else {
                emit(line);
            }
        }
        // Dòng dở dang không thể là dòng mở khối gợi ý nếu đã dài hơn nó nhiều: đưa ra ngay để chữ hiện dần.
        if (!inSuggestions && pending.length() > 40) {
            emit(pending.toString());
            pending.setLength(0);
        }
    }

    /** Kết thúc luồng: dòng cuối không có dấu xuống dòng cũng được xét. */
    void finish() {
        if (!inSuggestions && !pending.isEmpty()) {
            if (MARKER.matcher(pending.toString().strip()).matches()) {
                inSuggestions = true;
            } else {
                emit(pending.toString());
            }
            pending.setLength(0);
        }
    }

    /** Phần đã hiện, bỏ khoảng trắng thừa ở cuối. */
    String shown() {
        return shown.toString().strip();
    }

    /** Tối đa ba câu hỏi gợi ý mô hình viết ra. */
    List<String> suggestions() {
        List<String> found = new ArrayList<>();
        for (String line : tail.toString().split("\n")) {
            String question = BULLET.matcher(line).replaceFirst("").strip();
            if (!question.isEmpty() && found.size() < 3) {
                found.add(question.length() > 200 ? question.substring(0, 200) : question);
            }
        }
        return found;
    }

    private void emit(String text) {
        shown.append(text);
        visible.accept(text);
    }
}
