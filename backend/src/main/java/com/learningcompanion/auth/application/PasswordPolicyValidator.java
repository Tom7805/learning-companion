package com.learningcompanion.auth.application;

import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

/** Quy tắc mật khẩu dùng chung cho đăng ký, đổi và đặt lại mật khẩu (QTN-02). */
@Component
public class PasswordPolicyValidator {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;
    private static final String FIELD = "password";

    private final BreachedPasswordChecker breachedPasswordChecker;

    public PasswordPolicyValidator(BreachedPasswordChecker breachedPasswordChecker) {
        this.breachedPasswordChecker = breachedPasswordChecker;
    }

    public void validate(String password) {
        int length = password == null ? 0 : password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_SHORT, FIELD);
        }
        if (length > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_LONG, FIELD);
        }
        if (breachedPasswordChecker.isBreached(password)) {
            throw new BusinessException(ErrorCode.PASSWORD_BREACHED, FIELD);
        }
    }
}
