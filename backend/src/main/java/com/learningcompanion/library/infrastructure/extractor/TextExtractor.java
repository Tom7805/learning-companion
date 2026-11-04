package com.learningcompanion.library.infrastructure.extractor;

import com.learningcompanion.library.domain.DocumentType;
import java.util.Set;

/**
 * Trích văn bản theo loại tệp (NCL-05-CN-003). Mỗi khối văn bản gắn vị trí trong tài liệu gốc: số trang, số trang trình
 * chiếu, thứ tự ảnh hoặc số đoạn văn (QTN-20). Bảng và công thức được giữ thành một khối riêng để chia đoạn không cắt
 * ngang. Trang quét và ảnh đi qua {@link PageRecognizer}. Nội dung tài liệu chỉ là dữ liệu (QTN-16): bộ trích không
 * diễn giải gì trong đó.
 */
public interface TextExtractor {

    Set<DocumentType> types();

    Extraction extract(byte[] file, String mimeType, PageRecognizer recognizer);
}
