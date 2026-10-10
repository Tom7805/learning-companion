package com.learningcompanion.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontendUrl, Auth auth, Mail mail, DevTools devTools) {

    public AppProperties {
        if (devTools == null) {
            devTools = new DevTools(false);
        }
    }

    public record Auth(
            Duration verificationTokenTtl,
            Duration resendCooldown,
            Duration sessionIdleTimeout,
            String sessionCookieName,
            boolean secureCookies,
            int registerLimitPerHour,
            int resendLimitPerHour,
            Duration registrationAttemptNoticeInterval,
            Duration rememberDeviceDuration,
            int maxFailedLogins,
            Duration lockoutDuration,
            int loginLimitPerHour,
            String deviceCookieName) {
    }

    public record Mail(String gateway, String fromAddress, String fromName) {
    }

    public record DevTools(boolean enabled) {
    }
}
