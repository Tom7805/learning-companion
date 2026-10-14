package com.learningcompanion.privacy.application;

import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.notification.application.NotificationService;
import com.learningcompanion.notification.domain.NotificationTargetType;
import com.learningcompanion.notification.domain.NotificationType;
import com.learningcompanion.privacy.domain.DataExportRequest;
import com.learningcompanion.privacy.domain.DataExportRequestRepository;
import com.learningcompanion.privacy.domain.DataExportStatus;
import com.learningcompanion.profile.domain.UserProfile;
import com.learningcompanion.profile.domain.UserProfileRepository;
import com.learningcompanion.shared.audit.AuditAction;
import com.learningcompanion.shared.audit.AuditLogger;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.mail.MailService;
import com.learningcompanion.shared.mail.MailTemplate;
import com.learningcompanion.shared.storage.FileStorageService;
import jakarta.persistence.EntityManager;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Dựng gói xuất ở luồng nền sau khi yêu cầu đã được lưu (NCL-01-CN-008): gom dữ liệu từ mọi contributor,
 * lưu mã hóa vào kho tệp, ghi nhật ký nội dung gói và gửi thư có đường dẫn tải. Lỗi bất kỳ chuyển lần xuất
 * sang thất bại, người học yêu cầu lại được ngay.
 */
@Component
public class DataExportJob {

    private static final Logger log = LoggerFactory.getLogger(DataExportJob.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter MAIL_TIME = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final DataExportRequestRepository exports;
    private final DataExportService service;
    private final ExportPackager packager;
    private final FileStorageService storage;
    private final UserAccountRepository accounts;
    private final UserProfileRepository profiles;
    private final AuditLogger audit;
    private final MailService mail;
    private final NotificationService notifications;
    private final AppProperties properties;
    private final EntityManager entityManager;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DataExportJob(DataExportRequestRepository exports, DataExportService service, ExportPackager packager,
                         FileStorageService storage, UserAccountRepository accounts, UserProfileRepository profiles,
                         AuditLogger audit, MailService mail, NotificationService notifications,
                         AppProperties properties, EntityManager entityManager, PlatformTransactionManager transactions,
                         Clock clock) {
        this.exports = exports;
        this.service = service;
        this.packager = packager;
        this.storage = storage;
        this.accounts = accounts;
        this.profiles = profiles;
        this.audit = audit;
        this.mail = mail;
        this.notifications = notifications;
        this.properties = properties;
        this.entityManager = entityManager;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener
    public void onQueued(ExportQueued event) {
        run(event.exportId(), event.ownerId());
    }

    void run(UUID exportId, UUID ownerId) {
        Boolean claimed = tx.execute(status -> exports.findByIdAndOwnerId(exportId, ownerId)
                .filter(export -> export.getStatus() == DataExportStatus.QUEUED)
                .map(export -> {
                    export.start(clock.instant());
                    return true;
                })
                .orElse(false));
        if (!Boolean.TRUE.equals(claimed)) {
            return;
        }
        String storedKey = null;
        try {
            UserAccount account = accounts.findById(ownerId).orElseThrow();
            ZoneId zone = zoneOf(ownerId);
            Instant generatedAt = clock.instant();
            ExportPackager.Built built = packager.build(ownerId, account.getEmail(), generatedAt, zone);
            String key = storage.store(ownerId, built.content());
            storedKey = key;
            String fileName = ExportPackager.packageName(generatedAt, zone) + ".zip";
            DataExportRequest ready = tx.execute(status -> complete(exportId, ownerId, key, fileName, built));
            sendReadyMail(account, ready, zone);
        } catch (RuntimeException ex) {
            log.error("Data export {} failed", exportId, ex);
            if (storedKey != null) {
                deleteQuietly(storedKey);
            }
            tx.executeWithoutResult(status -> exports.findByIdAndOwnerId(exportId, ownerId).ifPresent(export -> {
                export.fail("ERROR", clock.instant());
                audit.bySystem(AuditAction.DATA_EXPORT_FAILED, AuditObjectType.DATA_EXPORT, exportId,
                        Map.of("reason", "ERROR"));
            }));
        }
    }

    /**
     * Dọn định kỳ các gói quá bảy ngày mà người học không quay lại xem, để tệp không nằm trong kho lâu hơn
     * mức cần (QTN-32). Đây là việc bảo trì của hệ thống, không trả dữ liệu cho ai nên quét qua mọi chủ sở hữu.
     */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void sweep() {
        tx.executeWithoutResult(status -> {
            Instant now = clock.instant();
            List<DataExportRequest> due = entityManager.createQuery("""
                            select e from DataExportRequest e
                            where (e.status = :ready and e.expiresAt <= :now)
                               or (e.status in :inProgress and e.requestedAt <= :stalledBefore)""",
                            DataExportRequest.class)
                    .setParameter("ready", DataExportStatus.READY)
                    .setParameter("now", now)
                    .setParameter("inProgress", List.of(DataExportStatus.QUEUED, DataExportStatus.RUNNING))
                    .setParameter("stalledBefore", now.minus(DataExportService.STALL_LIMIT))
                    .getResultList();
            due.forEach(export -> service.refresh(export, now));
        });
    }

    private DataExportRequest complete(UUID exportId, UUID ownerId, String key, String fileName,
                                       ExportPackager.Built built) {
        DataExportRequest export = exports.findByIdAndOwnerId(exportId, ownerId).orElseThrow();
        Instant now = clock.instant();
        export.complete(key, fileName, built.content().length, built.fileCount(), built.sections(), now,
                DataExportService.VALID_FOR);
        // Chỉ giữ gói mới nhất: gói cũ còn hạn được thay thế và xóa khỏi kho.
        for (DataExportRequest older : exports.findAllByOwnerIdAndStatus(ownerId, DataExportStatus.READY)) {
            if (!older.getId().equals(exportId)) {
                older.expire().ifPresent(service::deleteAfterCommit);
                audit.bySystem(AuditAction.DATA_EXPORT_EXPIRED, AuditObjectType.DATA_EXPORT, older.getId(),
                        Map.of("reason", "SUPERSEDED", "supersededBy", exportId.toString()));
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sections", built.sections().stream()
                .map(section -> Map.of("id", section.id(), "files", section.fileCount()))
                .toList());
        details.put("fileCount", built.fileCount());
        details.put("sizeBytes", built.content().length);
        details.put("expiresAt", export.getExpiresAt().toString());
        audit.byUser(ownerId, AuditAction.DATA_EXPORT_COMPLETED, AuditObjectType.DATA_EXPORT, exportId, details,
                null);
        // Cùng giao dịch với lúc gói hoàn tất: tác vụ có chạy lại cũng chỉ một thông báo cho mỗi gói (QTN-10).
        notifications.notify(ownerId, NotificationType.DATA_EXPORT_READY, NotificationTargetType.DATA_EXPORT, exportId,
                Map.of("expiresAt", export.getExpiresAt().toString(), "sizeBytes", export.getSizeBytes(),
                        "fileCount", export.getFileCount()),
                "data-export-ready:" + exportId);
        return export;
    }

    private void sendReadyMail(UserAccount account, DataExportRequest export, ZoneId zone) {
        String downloadUrl = UriComponentsBuilder.fromUriString(properties.frontendUrl())
                .path("/exports/{id}").buildAndExpand(export.getId()).toUriString();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("displayName", account.getDisplayName());
        variables.put("downloadUrl", downloadUrl);
        variables.put("expiresAt", MAIL_TIME.withZone(zone).format(export.getExpiresAt()));
        variables.put("size", sizeLabel(export.getSizeBytes()));
        variables.put("sections", export.getSections().stream().map(DataExportRequest.Section::title).toList());
        mail.send(account.getEmail(), MailTemplate.DATA_EXPORT_READY, variables);
    }

    private ZoneId zoneOf(UUID ownerId) {
        Optional<String> zone = profiles.findByOwnerId(ownerId).map(UserProfile::getTimeZone);
        try {
            return zone.map(ZoneId::of).orElse(DEFAULT_ZONE);
        } catch (DateTimeException ex) {
            return DEFAULT_ZONE;
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Could not delete export file after failure", ex);
        }
    }

    static String sizeLabel(long bytes) {
        DecimalFormat format = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi")));
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return format.format(bytes / 1024.0) + " KB";
        }
        return format.format(bytes / (1024.0 * 1024)) + " MB";
    }

    /** Phát ra khi một yêu cầu xuất đã được lưu; luồng nền chỉ bắt đầu sau khi giao dịch đó hoàn tất. */
    public record ExportQueued(UUID exportId, UUID ownerId) {
    }
}
