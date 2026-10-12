package com.learningcompanion.privacy.application;

import com.learningcompanion.privacy.domain.ConsentPurpose;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thống kê sử dụng ẩn danh (TC-01): chỉ ghi khi người học đang đồng ý, và chỉ ghi tên sự kiện, đường dẫn
 * đã bỏ mọi mã bản ghi cùng ngày. Không lưu mã tài khoản, địa chỉ IP hay thời điểm chính xác.
 */
@Service
public class UsageAnalyticsService {

    private static final Pattern EVENT = Pattern.compile("[a-z_]{3,40}");
    private static final Pattern ID_SEGMENT = Pattern.compile(
            "/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|\\d+)(?=/|$)");
    private static final int MAX_ROUTE = 120;

    private final ConsentGuard consent;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public UsageAnalyticsService(ConsentGuard consent, JdbcTemplate jdbc, Clock clock) {
        this.consent = consent;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Trả về true nếu sự kiện được ghi; không đồng ý thì bỏ qua mà không báo lỗi. */
    @Transactional
    public boolean record(UUID userId, String event, String route) {
        if (!consent.allows(userId, ConsentPurpose.ANONYMOUS_USAGE_ANALYTICS)) {
            return false;
        }
        if (event == null || !EVENT.matcher(event).matches()) {
            return false;
        }
        jdbc.update("insert into usage_events (id, event, route, occurred_on) values (?, ?, ?, ?)",
                UUID.randomUUID(), event, anonymize(route), LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        return true;
    }

    /** Bỏ chuỗi truy vấn và thay mọi mã trong đường dẫn bằng ":id". */
    static String anonymize(String route) {
        if (route == null || !route.startsWith("/")) {
            return "/";
        }
        String path = route.split("[?#]", 2)[0];
        String cleaned = ID_SEGMENT.matcher(path).replaceAll("/:id");
        return cleaned.length() > MAX_ROUTE ? cleaned.substring(0, MAX_ROUTE) : cleaned;
    }
}
