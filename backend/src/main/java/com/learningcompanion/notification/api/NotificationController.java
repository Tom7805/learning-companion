package com.learningcompanion.notification.api;

import com.learningcompanion.notification.api.dto.NotificationResponse;
import com.learningcompanion.notification.application.NotificationService;
import com.learningcompanion.notification.infrastructure.NotificationSseHub;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Thông báo của chính người học đang đăng nhập (NCL-02-CN-002); thông báo của người khác luôn được trả lời 404. */
@RestController
@RequestMapping(ApiPaths.NOTIFICATIONS)
public class NotificationController {

    private final NotificationService notifications;
    private final NotificationSseHub hub;
    private final OwnerContext owner;

    public NotificationController(NotificationService notifications, NotificationSseHub hub, OwnerContext owner) {
        this.notifications = notifications;
        this.hub = hub;
        this.owner = owner;
    }

    @GetMapping
    public PageResponse list(@RequestParam(required = false) UUID before,
                             @RequestParam(required = false) Integer limit, HttpServletRequest request) {
        NotificationService.Page page = notifications.list(before, limit, ClientInfo.from(request));
        return new PageResponse(page.items().stream().map(NotificationResponse::of).toList(), page.nextCursor(),
                page.unreadCount());
    }

    @GetMapping("/unread-count")
    public UnreadResponse unreadCount() {
        return new UnreadResponse(notifications.unreadCount());
    }

    /** Kênh sự kiện "unread" mang số chưa đọc mới mỗi khi có thông báo mới hoặc thông báo được đọc. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return hub.open(owner.requireOwnerId(), notifications.unreadCount());
    }

    /** Đánh dấu đã đọc và cho biết mở ở đâu; đối tượng không còn thì {@code available} là false. */
    @PostMapping("/{id}/open")
    public OpenResponse open(@PathVariable UUID id, HttpServletRequest request) {
        NotificationService.Opened opened = notifications.open(id, ClientInfo.from(request));
        return new OpenResponse(opened.available(), opened.path(), opened.unreadCount());
    }

    @PostMapping("/read-all")
    public ReadAllResponse readAll() {
        return new ReadAllResponse(notifications.markAllRead(), 0);
    }

    public record PageResponse(List<NotificationResponse> items, UUID nextCursor, long unreadCount) {
    }

    public record UnreadResponse(long unreadCount) {
    }

    public record OpenResponse(boolean available, String path, long unreadCount) {
    }

    public record ReadAllResponse(int marked, long unreadCount) {
    }
}
