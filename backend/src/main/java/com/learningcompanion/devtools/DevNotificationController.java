package com.learningcompanion.devtools;

import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.notification.application.NotificationService;
import com.learningcompanion.notification.domain.NotificationTargetType;
import com.learningcompanion.notification.domain.NotificationType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ApiPaths;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thông báo mẫu cho môi trường thử (NCL-02-CN-002): nhiều thông báo cùng lúc để xem phân trang. Thông báo mẫu trỏ
 * tới gói dữ liệu không tồn tại nên mở ra trang "nội dung không còn". Không bao giờ được bật ở môi trường thật.
 */
@RestController
@RequestMapping(ApiPaths.DEV + "/notifications")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevNotificationController {

    private static final int MAX_SAMPLES = 60;

    private final UserAccountRepository accounts;
    private final NotificationService notifications;
    private final Clock clock;

    public DevNotificationController(UserAccountRepository accounts, NotificationService notifications, Clock clock) {
        this.accounts = accounts;
        this.notifications = notifications;
        this.clock = clock;
    }

    @PostMapping("/sample")
    public SampleResponse sample(@RequestBody SampleRequest request) {
        UserAccount account = accounts.findByEmail(request.email() == null ? ""
                        : request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        int count = request.count() == null ? 1 : Math.clamp(request.count(), 1, MAX_SAMPLES);
        int created = 0;
        for (int i = 0; i < count; i++) {
            created += notifications.notify(account.getId(), NotificationType.DATA_EXPORT_READY,
                    NotificationTargetType.DATA_EXPORT, UUID.randomUUID(),
                    Map.of("expiresAt", clock.instant().plus(Duration.ofDays(7)).toString(), "sizeBytes", 48_640,
                            "fileCount", 12),
                    "dev-sample:" + UUID.randomUUID()) ? 1 : 0;
        }
        return new SampleResponse(created);
    }

    public record SampleRequest(String email, Integer count) {
    }

    public record SampleResponse(int created) {
    }
}
