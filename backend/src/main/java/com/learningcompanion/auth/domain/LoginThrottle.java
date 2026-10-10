package com.learningcompanion.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * Số lần nhập sai mật khẩu liên tiếp theo địa chỉ thư (QTN-02). Theo dõi cả địa chỉ chưa đăng ký
 * để phản hồi "tạm khóa" giống nhau, không ai dò được địa chỉ nào có tài khoản.
 */
@Entity
@Table(name = "login_throttles")
public class LoginThrottle {

    @Id
    private String email;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failed_at", nullable = false)
    private Instant lastFailedAt;

    protected LoginThrottle() {
    }

    public static LoginThrottle start(String email, Instant now) {
        LoginThrottle throttle = new LoginThrottle();
        throttle.email = email;
        throttle.lastFailedAt = now;
        return throttle;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public Duration remainingLock(Instant now) {
        return isLocked(now) ? Duration.between(now, lockedUntil) : Duration.ZERO;
    }

    /** Ghi một lần nhập sai; đủ số lần cho phép thì khóa. Hết thời gian khóa thì đếm lại từ đầu. */
    public void recordFailure(Instant now, int maxFailures, Duration lockout) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            failedCount = 0;
            lockedUntil = null;
        }
        failedCount++;
        lastFailedAt = now;
        if (failedCount >= maxFailures) {
            lockedUntil = now.plus(lockout);
        }
    }

    public int remainingAttempts(int maxFailures) {
        return Math.max(0, maxFailures - failedCount);
    }

    public String getEmail() {
        return email;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
