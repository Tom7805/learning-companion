package com.learningcompanion.library.application;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Cho kho tài liệu biết tài liệu đang được nơi nào dùng mà không phụ thuộc ngược vào chức năng đó (QTN-17). Không gian
 * học cài giao diện này; kho hỏi trước khi đưa tài liệu vào thùng rác (NCL-06-CN-001 TC-03).
 */
public interface DocumentUsageProbe {

    /** Các nơi đang dùng ít nhất một tài liệu trong danh sách, mỗi nơi một lần, theo tên. */
    List<UsedBy> usedBy(UUID ownerId, Collection<UUID> documentIds);

    record UsedBy(UUID id, String name) {
    }
}
