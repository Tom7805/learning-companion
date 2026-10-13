package com.learningcompanion.shared.export;

/** Định dạng mở dùng trong gói xuất dữ liệu (QTN-32); tệp hướng dẫn đọc nêu định dạng của từng phần. */
public enum ExportFormat {
    /** Dữ liệu có cấu trúc, UTF-8. */
    JSON("JSON"),
    /** Bảng tính văn bản, UTF-8 có BOM để mở đúng tiếng Việt trong Excel. */
    CSV("CSV"),
    /** Văn bản định dạng nhẹ. */
    MARKDOWN("Markdown"),
    /** Tệp gốc đúng như lúc người học đưa lên. */
    ORIGINAL("tệp gốc");

    private final String label;

    ExportFormat(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
