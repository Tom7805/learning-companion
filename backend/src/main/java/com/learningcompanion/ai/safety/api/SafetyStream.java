package com.learningcompanion.ai.safety.api;

import com.learningcompanion.ai.safety.application.SafeGenerationService;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Luồng sự kiện chung cho lời gọi sinh nội dung đi qua bộ lọc an toàn (NCL-04-CN-004): "chunk" cho từng đoạn đã qua
 * bộ lọc đầu ra; "safety" khi câu hỏi bị chặn, nội dung sinh ra bị thay hay câu hỏi nhận câu trả lời ân cần; rồi
 * "done" (kèm số các đoạn nguồn bị gắn cờ) hoặc "error" (mã lỗi).
 */
public final class SafetyStream {

    private SafetyStream() {
    }

    /**
     * Chạy {@code work} trên một luồng ảo và đẩy kết quả vào luồng sự kiện.
     *
     * @param done thêm trường riêng của chức năng vào sự kiện "done"
     */
    public static SseEmitter start(String threadName,
                                   Function<java.util.function.Consumer<String>, SafeGenerationService.Outcome> work,
                                   BiConsumer<SafeGenerationService.Outcome, Map<String, Object>> done) {
        SseEmitter emitter = new SseEmitter(0L);
        Thread.ofVirtual().name(threadName).start(() -> {
            try {
                SafeGenerationService.Outcome outcome = work.apply(text -> send(emitter, "chunk", Map.of("text", text)));
                if (outcome.status() != SafeGenerationService.Status.ANSWERED) {
                    send(emitter, "safety", safety(outcome));
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", outcome.status().name());
                body.put("flaggedSources", outcome.flaggedSources());
                if (outcome.result() != null) {
                    body.put("providerCode", outcome.result().providerCode());
                    body.put("inputUnits", outcome.result().usage().inputUnits());
                    body.put("outputUnits", outcome.result().usage().outputUnits());
                }
                done.accept(outcome, body);
                send(emitter, "done", body);
                emitter.complete();
            } catch (BusinessException ex) {
                send(emitter, "error", Map.of("code", ex.code().name(), "message", ex.getMessage()));
                emitter.complete();
            } catch (RuntimeException ex) {
                send(emitter, "error", Map.of("code", ErrorCode.INTERNAL_ERROR.name(),
                        "message", ErrorCode.INTERNAL_ERROR.defaultMessage()));
                emitter.complete();
            }
        });
        return emitter;
    }

    /** Phần mô tả can thiệp của bộ lọc, dùng chung cho luồng sự kiện và phản hồi JSON. */
    public static Map<String, Object> safety(SafeGenerationService.Outcome outcome) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", outcome.status().name());
        body.put("stage", outcome.stage() == null ? null : outcome.stage().name());
        body.put("category", outcome.category() == null ? null : outcome.category().name());
        body.put("support", outcome.support());
        return body;
    }

    private static void send(SseEmitter emitter, String event, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            // Trình duyệt đã đóng kết nối; lời gọi vẫn chạy hết và vẫn được đo.
        }
    }
}
