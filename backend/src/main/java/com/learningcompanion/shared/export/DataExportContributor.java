package com.learningcompanion.shared.export;

import java.util.List;
import java.util.UUID;

/**
 * Mỗi chức năng có dữ liệu của người học cài một contributor để phần dữ liệu đó có mặt trong gói xuất
 * (NCL-01-CN-008, QTN-35). Chức năng ra đời sau chỉ cần thêm một bean loại này là tự có trong gói,
 * không phải sửa bộ xuất.
 *
 * <p>Contributor chỉ đọc dữ liệu của {@code ownerId} truyền vào, luôn qua tầng truy vấn theo chủ sở hữu.
 */
public interface DataExportContributor {

    /** Thư mục của phần này trong gói, ví dụ "library". */
    String section();

    /** Tên phần hiển thị trong tệp hướng dẫn đọc. */
    String title();

    /** Mô tả ngắn từng tệp hoặc thư mục, đưa vào tệp hướng dẫn đọc. */
    List<String> describe();

    List<ExportFormat> formats();

    /** Thứ tự trong gói, nhỏ đứng trước. */
    default int order() {
        return 100;
    }

    void contribute(UUID ownerId, ExportWriter writer);
}
