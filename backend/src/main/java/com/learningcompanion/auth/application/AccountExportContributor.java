package com.learningcompanion.auth.application;

import com.learningcompanion.auth.domain.ExternalIdentity;
import com.learningcompanion.auth.domain.ExternalIdentityRepository;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.auth.domain.UserSession;
import com.learningcompanion.auth.domain.UserSessionRepository;
import com.learningcompanion.shared.export.DataExportContributor;
import com.learningcompanion.shared.export.ExportFormat;
import com.learningcompanion.shared.export.ExportWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Phần "Tài khoản" của gói xuất: thông tin tài khoản, tài khoản bên ngoài đã liên kết và các lần đăng nhập. */
@Component
public class AccountExportContributor implements DataExportContributor {

    private final UserAccountRepository accounts;
    private final ExternalIdentityRepository identities;
    private final UserSessionRepository sessions;
    private final Clock clock;

    public AccountExportContributor(UserAccountRepository accounts, ExternalIdentityRepository identities,
                                    UserSessionRepository sessions, Clock clock) {
        this.accounts = accounts;
        this.identities = identities;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public String section() {
        return "account";
    }

    @Override
    public String title() {
        return "Tài khoản";
    }

    @Override
    public List<String> describe() {
        return List.of(
                "account.json: địa chỉ thư, tên hiển thị, vai trò, các mốc tạo, xác thực, đổi mật khẩu và các tài "
                        + "khoản bên ngoài đã liên kết. Không chứa mật khẩu.",
                "sessions.csv: các lần đăng nhập theo thiết bị (trình duyệt, hệ điều hành, vị trí gần đúng, thời điểm).");
    }

    @Override
    public List<ExportFormat> formats() {
        return List.of(ExportFormat.JSON, ExportFormat.CSV);
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(UUID ownerId, ExportWriter writer) {
        UserAccount account = accounts.findById(ownerId).orElse(null);
        if (account == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", account.getId());
        data.put("email", account.getEmail());
        data.put("displayName", account.getDisplayName());
        data.put("role", account.getRole().name());
        data.put("status", account.getStatus().name());
        data.put("hasPassword", account.hasPassword());
        data.put("createdAt", account.getCreatedAt());
        data.put("emailVerifiedAt", account.getEmailVerifiedAt());
        data.put("passwordChangedAt", account.getPasswordChangedAt());
        data.put("linkedAccounts", identities.findByUserIdOrderByLinkedAtAsc(ownerId).stream()
                .map(AccountExportContributor::identity).toList());
        writer.json("account.json", data);

        Instant now = clock.instant();
        List<List<?>> rows = sessions.findByUserId(ownerId).stream()
                .sorted(Comparator.comparing(UserSession::getCreatedAt).reversed())
                .<List<?>>map(session -> Arrays.asList(
                        session.getCreatedAt(), session.getLastActiveAt(), session.device().browser(),
                        session.device().operatingSystem(),
                        session.device().deviceType() == null ? null : session.device().deviceType().name(),
                        session.device().location(), session.isRememberDevice(), session.state(now).name(),
                        session.getRevokedAt(), session.getRevokeReason()))
                .toList();
        writer.csv("sessions.csv", List.of("signed_in_at", "last_active_at", "browser", "operating_system",
                "device_type", "approximate_location", "remember_device", "state", "signed_out_at",
                "sign_out_reason"), rows);
    }

    private static Map<String, Object> identity(ExternalIdentity identity) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", identity.getProvider().displayName());
        data.put("email", identity.getEmail());
        data.put("linkedAt", identity.getLinkedAt());
        data.put("lastUsedAt", identity.getLastUsedAt());
        return data;
    }
}
