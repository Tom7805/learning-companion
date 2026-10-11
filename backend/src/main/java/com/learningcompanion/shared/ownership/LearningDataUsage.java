package com.learningcompanion.shared.ownership;

import java.util.UUID;

/**
 * Mỗi phân hệ học tập cho biết một người học đang có bao nhiêu bản ghi và dùng bao nhiêu dung lượng.
 * Quản trị viên chỉ thấy các con số này, không bao giờ thấy nội dung (QTN-07).
 */
public interface LearningDataUsage {

    Usage usageOf(UUID ownerId);

    record Usage(long records, long bytes) {

        public Usage plus(Usage other) {
            return new Usage(records + other.records, bytes + other.bytes);
        }
    }
}
