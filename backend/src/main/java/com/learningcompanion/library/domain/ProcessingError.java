package com.learningcompanion.library.domain;

/** Lý do một tài liệu xử lý lỗi (NCL-05-CN-003); giao diện dịch mã ra lời và gợi ý cách khắc phục. */
public enum ProcessingError {
    /** Không trích được chữ nào, ví dụ tệp chỉ có trang trắng: gợi ý tải bản rõ hơn (TC-04). */
    NO_TEXT(false),
    /** Dịch vụ nhận dạng chữ vẫn lỗi ở một trang sau khi thử lại riêng trang đó (TC-03). */
    OCR_FAILED(true),
    /** Dịch vụ biểu diễn ngữ nghĩa lỗi khi lập chỉ mục. */
    INDEXING_FAILED(true),
    /** Tệp không đọc được nội dung (hỏng hoặc cấu trúc lạ). */
    UNREADABLE(false),
    /** Loại tài liệu chưa xử lý được, ví dụ âm thanh và video. */
    UNSUPPORTED(false),
    /** Lỗi không lường trước của hệ thống. */
    INTERNAL(true);

    private final boolean retryable;

    ProcessingError(boolean retryable) {
        this.retryable = retryable;
    }

    /** Lỗi tạm thời, thử lại có thể được; lỗi do chính tệp thì cần tệp khác. */
    public boolean retryable() {
        return retryable;
    }
}
