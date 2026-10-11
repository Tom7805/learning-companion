package com.learningcompanion.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontendUrl, Auth auth, Mail mail, DevTools devTools, Storage storage) {

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
            String deviceCookieName,
            Duration passwordResetTokenTtl,
            int passwordResetLimitPerHour) {
    }

    public record Mail(String gateway, String fromAddress, String fromName) {
    }

    public record DevTools(boolean enabled) {
    }

    /**
     * Kho tệp gốc: tệp được mã hóa trước khi ghi và chỉ mở qua đường dẫn ký có hạn (NCL-01-CN-004).
     * Khóa mã hóa và khóa ký là 32 byte dạng Base64, môi trường thật đặt qua biến môi trường.
     */
    public record Storage(String localRoot, String encryptionKey, String signingKey, Duration signedUrlTtl) {
    }
}
