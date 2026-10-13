package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.LoginThrottleRepository;
import com.learningcompanion.auth.domain.OAuthPendingRepository;
import com.learningcompanion.auth.domain.UserSessionRepository;
import com.learningcompanion.shared.erasure.PersonalDataEraser;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Dữ liệu đăng nhập khi xóa tài khoản: bộ đếm nhập sai và bước đăng nhập qua nhà cung cấp còn dang dở được tra theo
 * địa chỉ thư nên không mất theo khóa ngoại, phải xóa ở đây. Phiên đăng nhập, mã xác thực, tài khoản liên kết mất
 * theo khóa ngoại tới tài khoản; số phiên được đếm để ghi vào dấu vết.
 */
@Component
public class AccountAccessEraser implements PersonalDataEraser {

    private final LoginThrottleRepository throttles;
    private final OAuthPendingRepository pendings;
    private final UserSessionRepository sessions;

    public AccountAccessEraser(LoginThrottleRepository throttles, OAuthPendingRepository pendings,
                               UserSessionRepository sessions) {
        this.throttles = throttles;
        this.pendings = pendings;
        this.sessions = sessions;
    }

    @Override
    public String section() {
        return "account";
    }

    @Override
    public int erase(Subject subject, List<String> filesToDelete) {
        throttles.deleteById(subject.email());
        pendings.deleteByEmail(subject.email());
        return sessions.findByUserId(subject.accountId()).size();
    }
}
