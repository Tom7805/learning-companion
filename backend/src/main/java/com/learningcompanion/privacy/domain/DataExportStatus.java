package com.learningcompanion.privacy.domain;

/** Vòng đời một lần xuất dữ liệu. */
public enum DataExportStatus {
    /** Đã nhận yêu cầu, chờ luồng nền dựng gói. */
    QUEUED,
    RUNNING,
    /** Gói đã sẵn sàng và còn trong bảy ngày tải. */
    READY,
    /** Dựng gói thất bại; không tính vào giới hạn mỗi ngày một lần. */
    FAILED,
    /** Quá bảy ngày, tệp đã bị xóa. */
    EXPIRED;

    public boolean inProgress() {
        return this == QUEUED || this == RUNNING;
    }
}
