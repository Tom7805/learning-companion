package com.learningcompanion.shared.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Chi tiết nhật ký cho một lần đổi cấu hình: mỗi trường đã đổi kèm giá trị trước và sau (NCL-03-CN-001). Chỉ dùng cho
 * giá trị cấu hình (chế độ màu, công tắc tính năng...), không bao giờ cho dữ liệu cá nhân hay nội dung học tập.
 */
public final class AuditChanges {

    private final List<Map<String, Object>> changes = new ArrayList<>();

    private AuditChanges() {
    }

    public static AuditChanges of() {
        return new AuditChanges();
    }

    /** Ghi một trường nếu giá trị thật sự đổi. */
    public AuditChanges field(String name, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("field", name);
            change.put("before", before);
            change.put("after", after);
            changes.add(change);
        }
        return this;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public Map<String, Object> toDetails() {
        return Map.of("changes", List.copyOf(changes));
    }
}
