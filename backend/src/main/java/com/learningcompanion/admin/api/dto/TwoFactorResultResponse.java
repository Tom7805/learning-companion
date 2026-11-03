package com.learningcompanion.admin.api.dto;

import com.learningcompanion.auth.api.dto.AccountResponse;
import java.time.Instant;
import java.util.List;

/**
 * Kết quả bước mã xác thực. Đã đăng nhập thì có tài khoản và hạn cứng của phiên; mã khôi phục chỉ có (một lần duy
 * nhất) ngay sau khi thiết lập. Còn phải thiết lập lại ứng dụng sinh mã thì {@code stage} là ENROLL.
 */
public record TwoFactorResultResponse(boolean signedIn, String stage, AccountResponse account,
                                      Instant sessionExpiresAt, List<String> recoveryCodes) {
}
