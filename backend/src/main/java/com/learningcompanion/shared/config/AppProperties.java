package com.learningcompanion.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontendUrl, Auth auth, Mail mail, DevTools devTools, Storage storage,
                            OAuth oauth) {

    public AppProperties {
        if (devTools == null) {
            devTools = new DevTools(false);
        }
        if (oauth == null) {
            oauth = new OAuth("disabled", null, null, Duration.ofMinutes(10));
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

    /**
     * Đăng nhập qua nhà cung cấp định danh (NCL-01-CN-007). {@code mode}: "disabled", "google" (môi trường thật,
     * cần mã ứng dụng) hoặc "mock" (máy phát triển và kiểm thử, có trang cho phép quyền giả lập).
     */
    public record OAuth(String mode, String googleClientId, String googleClientSecret, Duration flowTtl) {

        public boolean enabled() {
            return mode != null && !"disabled".equals(mode);
        }
    }
}
