package com.learningcompanion.auth.infrastructure;

import com.learningcompanion.auth.domain.IdentityProvider;

/**
 * Kết nối tới nhà cung cấp định danh theo OpenID Connect, luồng mã ủy quyền có PKCE (QTN-04: thay được).
 * Chỉ xin quyền đọc tên và địa chỉ thư ({@link #SCOPES}).
 */
public interface OidcProviderClient {

    String SCOPES = "openid email profile";

    IdentityProvider provider();

    /** Địa chỉ trang cho phép quyền của nhà cung cấp, kèm mã chống giả mạo và mã thử thách PKCE. */
    String authorizationUrl(String state, String codeChallenge, String redirectUri);

    /** Đổi mã ủy quyền lấy thông tin người dùng; nhà cung cấp kiểm mã xác minh PKCE. */
    ExternalProfile exchange(String code, String codeVerifier, String redirectUri);

    /** Thông tin tối thiểu nhận về: mã định danh bất biến, địa chỉ thư (đã xác thực hay chưa) và tên. */
    record ExternalProfile(String subject, String email, boolean emailVerified, String name) {
    }
}
