package com.learningcompanion.library.domain;

/**
 * Trạng thái xử lý của tài liệu (trích văn bản, nhận dạng chữ, lập chỉ mục). Tài liệu mới tải lên đi từ
 * {@code PENDING} qua {@code PROCESSING} tới {@code READY} hoặc {@code FAILED}; kho tài liệu lọc được theo trạng thái.
 */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}
