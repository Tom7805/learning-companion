package com.learningcompanion.privacy.api;

import com.learningcompanion.privacy.application.UsageAnalyticsService;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.web.ApiPaths;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Nhận sự kiện thống kê ẩn danh; chỉ ghi khi người học đang đồng ý mục đích này. */
@RestController
@RequestMapping(ApiPaths.ANALYTICS)
public class UsageAnalyticsController {

    private final UsageAnalyticsService analytics;

    public UsageAnalyticsController(UsageAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Recorded record(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody UsageEvent event) {
        return new Recorded(analytics.record(user.userId(), event.event(), event.route()));
    }

    public record UsageEvent(String event, String route) {
    }

    public record Recorded(boolean recorded) {
    }
}
