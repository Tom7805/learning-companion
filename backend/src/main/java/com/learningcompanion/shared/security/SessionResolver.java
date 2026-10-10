package com.learningcompanion.shared.security;

/** Tra phiên từ chuỗi bí mật trong cookie; mô-đun auth cung cấp phần hiện thực. */
public interface SessionResolver {

    SessionLookup resolve(String rawToken);

    /** Kết quả tra phiên: có người dùng, hoặc lý do phiên không còn dùng được. */
    record SessionLookup(AuthenticatedUser user, Problem problem) {

        public static SessionLookup valid(AuthenticatedUser user) {
            return new SessionLookup(user, null);
        }

        public static SessionLookup invalid(Problem problem) {
            return new SessionLookup(null, problem);
        }
    }

    enum Problem {
        /** Hết thời gian không thao tác hoặc hết 30 ngày ghi nhớ. */
        EXPIRED,
        /** Bị đăng xuất từ thiết bị khác hoặc từ thư cảnh báo. */
        REVOKED,
        /** Chuỗi bí mật không khớp phiên nào. */
        UNKNOWN
    }
}
