package com.learningcompanion.notification.infrastructure;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Kênh đẩy số thông báo chưa đọc tới các thẻ trình duyệt đang mở của từng người học (NCL-02-CN-002), để biểu tượng
 * chuông cập nhật ngay mà không cần tải lại trang. Mỗi kênh tự đóng sau một khoảng; trình duyệt tự nối lại và nhận
 * lại số mới nhất, nhờ vậy phiên đăng nhập cũng được kiểm lại định kỳ.
 *
 * <p>Kênh nằm trong bộ nhớ của từng máy chủ; khi chạy nhiều máy chủ cần thêm lớp phát tin chung (ví dụ Redis).
 */
@Component
public class NotificationSseHub {

    public static final String EVENT = "unread";
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(15);
    /** Đủ cho vài thẻ và vài thiết bị; quá thì đóng kênh cũ nhất. */
    static final int MAX_STREAMS_PER_OWNER = 8;
    private static final long RECONNECT_MILLIS = 3_000;

    private final Map<UUID, List<SseEmitter>> streams = new ConcurrentHashMap<>();

    public SseEmitter open(UUID ownerId, long unreadCount) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        List<SseEmitter> owned = streams.computeIfAbsent(ownerId, key -> new CopyOnWriteArrayList<>());
        owned.add(emitter);
        while (owned.size() > MAX_STREAMS_PER_OWNER) {
            owned.removeFirst().complete();
        }
        Runnable forget = () -> forget(ownerId, emitter);
        emitter.onCompletion(forget);
        emitter.onTimeout(forget);
        emitter.onError(error -> forget.run());
        send(ownerId, emitter, SseEmitter.event().name(EVENT).reconnectTime(RECONNECT_MILLIS)
                .data(new Unread(unreadCount, false), MediaType.APPLICATION_JSON));
        return emitter;
    }

    public boolean hasStreams(UUID ownerId) {
        List<SseEmitter> owned = streams.get(ownerId);
        return owned != null && !owned.isEmpty();
    }

    public void publish(UUID ownerId, long unreadCount, boolean created) {
        for (SseEmitter emitter : streams.getOrDefault(ownerId, List.of())) {
            send(ownerId, emitter, SseEmitter.event().name(EVENT)
                    .data(new Unread(unreadCount, created), MediaType.APPLICATION_JSON));
        }
    }

    /** Dòng chú thích định kỳ giữ kênh mở qua các proxy hay cắt kết nối im lặng. */
    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    public void heartbeat() {
        streams.forEach((ownerId, owned) -> owned.forEach(emitter ->
                send(ownerId, emitter, SseEmitter.event().comment("ping"))));
    }

    private void send(UUID ownerId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException ex) {
            // Thẻ đã đóng hoặc mạng đứt: chỉ bỏ kênh, máy chủ web tự dọn kết nối; trình duyệt tự nối lại nếu còn mở.
            forget(ownerId, emitter);
        }
    }

    private void forget(UUID ownerId, SseEmitter emitter) {
        streams.computeIfPresent(ownerId, (key, owned) -> {
            owned.remove(emitter);
            return owned.isEmpty() ? null : owned;
        });
    }

    /** Dữ liệu của sự kiện "unread". */
    public record Unread(long unreadCount, boolean created) {
    }
}
