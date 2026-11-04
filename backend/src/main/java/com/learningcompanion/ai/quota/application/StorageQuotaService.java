package com.learningcompanion.ai.quota.application;

import com.learningcompanion.ai.quota.domain.QuotaPolicy;
import com.learningcompanion.ai.quota.domain.QuotaPolicyRepository;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.LearningDataUsage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hạn mức dung lượng lưu trữ chung cho mọi người học (NCL-04-CN-003). Dung lượng đã dùng là tổng các phân hệ học tập
 * báo qua {@link LearningDataUsage}; mọi chức năng lưu tệp gọi {@link #requireRoom} trước khi ghi. Hết chỗ chỉ chặn
 * việc thêm tệp mới, mọi tài liệu đã có vẫn mở và học được.
 */
@Service
public class StorageQuotaService {

    private final QuotaPolicyRepository policies;
    // Đọc lười để tránh vòng phụ thuộc: chính các phân hệ lưu tệp cũng gọi lại dịch vụ này.
    private final ObjectProvider<LearningDataUsage> usageSources;

    public StorageQuotaService(QuotaPolicyRepository policies, ObjectProvider<LearningDataUsage> usageSources) {
        this.policies = policies;
        this.usageSources = usageSources;
    }

    @Transactional(readOnly = true)
    public StorageUsage usage(UUID ownerId) {
        long used = usageSources.orderedStream().mapToLong(source -> source.usageOf(ownerId).bytes()).sum();
        QuotaPolicy policy = policies.current();
        return new StorageUsage(policy.getStorageLimitBytes(), used, policy.getMaxUploadFileBytes());
    }

    /** Chặn tệp lớn hơn dung lượng tối đa một tệp do quản trị viên đặt (QTN-19); chi tiết kèm giới hạn. */
    @Transactional(readOnly = true)
    public void requireFileSize(long bytes) {
        long max = policies.current().getMaxUploadFileBytes();
        if (bytes > max) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("maxBytes", max);
            details.put("sizeBytes", bytes);
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, null, details);
        }
    }

    /** Chặn khi thêm {@code additionalBytes} sẽ vượt hạn mức dung lượng. */
    @Transactional(readOnly = true)
    public void requireRoom(UUID ownerId, long additionalBytes) {
        StorageUsage usage = usage(ownerId);
        if (usage.usedBytes() + Math.max(0, additionalBytes) > usage.limitBytes()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("limitBytes", usage.limitBytes());
            details.put("usedBytes", usage.usedBytes());
            details.put("requiredBytes", additionalBytes);
            throw new BusinessException(ErrorCode.STORAGE_QUOTA_EXCEEDED, null, details);
        }
    }

    public record StorageUsage(long limitBytes, long usedBytes, long maxFileBytes) {

        public long remainingBytes() {
            return Math.max(0, limitBytes - usedBytes);
        }
    }
}
