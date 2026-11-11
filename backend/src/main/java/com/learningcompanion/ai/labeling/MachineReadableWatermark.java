package com.learningcompanion.ai.labeling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Đính dấu nhận biết máy đọc được vào tệp văn bản định dạng nhẹ (NCL-04-CN-005 TC-02): khối siêu dữ liệu đầu tệp
 * ({@code ---} … {@code ---}) ghi {@code ai_generated: true}, chức năng đã sinh, thời điểm, việc đã sửa tay và mã băm
 * của phần nội dung để biết nội dung có bị đổi sau khi xuất. Đính xong luôn tự đọc lại; dấu không đọc lại được thì báo
 * lỗi để không bao giờ xuất tệp thiếu dấu (TC-04).
 */
@Component
public class MachineReadableWatermark {

    static final String FIELD = "ai_generated";
    private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\n(.*?)\\n---\\n", Pattern.DOTALL);
    private static final Pattern DIGEST = Pattern.compile("^content_sha256: ([0-9a-f]{64})$", Pattern.MULTILINE);

    /** Số lần đính dấu tiếp theo bị giả lập lỗi (âm: lỗi cho tới khi đặt lại); chỉ công cụ phát triển đặt. */
    private final AtomicInteger simulatedFailures = new AtomicInteger();

    /** Bộ đính dấu không gắn được dấu: tệp không được xuất. */
    public static class WatermarkException extends RuntimeException {
        public WatermarkException(String message) {
            super(message);
        }
    }

    /** Tệp gồm khối siêu dữ liệu có dấu nhận biết rồi tới {@code body}. */
    public String apply(String title, String body, AiProvenance provenance) {
        if (consumeFailure()) {
            throw new WatermarkException("Bộ đính dấu nhận biết đang lỗi (giả lập).");
        }
        String content = body.endsWith("\n") ? body : body + "\n";
        StringBuilder meta = new StringBuilder("---\n")
                .append("title: ").append(quoted(title)).append('\n')
                .append(FIELD).append(": true\n")
                .append("ai_label: ").append(quoted(AiLabelService.LABEL)).append('\n')
                .append("generator: ").append(quoted(AiProvenance.GENERATOR)).append('\n')
                .append("ai_feature: ").append(provenance.feature()).append('\n')
                .append("content_id: ").append(provenance.contentId()).append('\n')
                .append("generated_at: ").append(provenance.generatedAt()).append('\n')
                .append("edited: ").append(provenance.edited()).append('\n');
        if (provenance.edited()) {
            meta.append("edited_at: ").append(provenance.editedAt()).append('\n');
        }
        meta.append("content_sha256: ").append(sha256(content)).append('\n').append("---\n");
        String file = meta + content;
        if (!verify(file)) {
            throw new WatermarkException("Dấu nhận biết vừa đính không đọc lại được.");
        }
        return file;
    }

    /** Tệp có dấu nhận biết hợp lệ và phần nội dung chưa bị đổi sau khi đính. */
    public boolean verify(String file) {
        Matcher front = FRONT_MATTER.matcher(file);
        if (!front.find()) {
            return false;
        }
        String meta = front.group(1);
        Matcher digest = DIGEST.matcher(meta);
        return meta.lines().anyMatch((FIELD + ": true")::equals) && digest.find()
                && digest.group(1).equals(sha256(file.substring(front.end())));
    }

    /** Cho {@code calls} lần đính dấu tiếp theo lỗi; số âm: lỗi cho tới khi đặt lại (TC-04). */
    public void simulateFailures(int calls) {
        simulatedFailures.set(calls);
    }

    public void reset() {
        simulatedFailures.set(0);
    }

    private boolean consumeFailure() {
        int left = simulatedFailures.getAndUpdate(n -> n > 0 ? n - 1 : n);
        return left != 0;
    }

    private static String quoted(String value) {
        String clean = value == null ? "" : value.replace("\r", " ").replace("\n", " ");
        return '"' + clean.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
