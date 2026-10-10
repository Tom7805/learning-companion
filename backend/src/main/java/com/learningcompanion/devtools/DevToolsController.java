package com.learningcompanion.devtools;

import com.learningcompanion.shared.mail.MockMailGateway;
import com.learningcompanion.shared.time.AdjustableClock;
import com.learningcompanion.shared.web.ApiPaths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Công cụ chỉ có ở môi trường phát triển và kiểm thử: xem hộp thư mô phỏng và tua đồng hồ
 * để thử các mốc hết hạn. Không bao giờ được bật ở môi trường thật.
 */
@RestController
@RequestMapping(ApiPaths.DEV)
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevToolsController {

    private final ObjectProvider<MockMailGateway> mailbox;
    private final AdjustableClock clock;

    public DevToolsController(ObjectProvider<MockMailGateway> mailbox, AdjustableClock clock) {
        this.mailbox = mailbox;
        this.clock = clock;
    }

    @GetMapping("/mailbox")
    public List<MockMailGateway.CapturedMail> mailbox(@RequestParam(required = false) String to) {
        MockMailGateway gateway = mailbox.getIfAvailable();
        if (gateway == null) {
            return List.of();
        }
        return gateway.messages().stream()
                .filter(mail -> to == null || mail.to().equalsIgnoreCase(to))
                .toList();
    }

    @DeleteMapping("/mailbox")
    public ResponseEntity<Void> clearMailbox() {
        mailbox.ifAvailable(MockMailGateway::clear);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/clock")
    public ClockState clock() {
        return new ClockState(clock.instant(), clock.offset().toSeconds());
    }

    @PostMapping("/clock/advance")
    public ClockState advance(@RequestBody AdvanceRequest request) {
        clock.advance(Duration.ofSeconds(request.seconds()));
        return clock();
    }

    @PostMapping("/clock/reset")
    public ClockState reset() {
        clock.reset();
        return clock();
    }

    public record AdvanceRequest(long seconds) {
    }

    public record ClockState(Instant now, long offsetSeconds) {
    }
}
