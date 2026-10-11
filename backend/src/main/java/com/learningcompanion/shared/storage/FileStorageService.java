package com.learningcompanion.shared.storage;

import java.util.UUID;

/**
 * Kho tệp gốc của người học (QTN-04: kết nối bên ngoài qua lớp thay thế được).
 * Bản hiện tại ghi xuống đĩa cục bộ; bản S3/MinIO cho môi trường thật cài cùng giao diện này.
 * Tệp luôn được mã hóa trước khi rời bộ nhớ ứng dụng và chỉ được mở qua đường dẫn ký có hạn.
 */
public interface FileStorageService {

    /** Ghi tệp vào vùng riêng của chủ sở hữu, trả về khóa để lưu cùng bản ghi. */
    String store(UUID ownerId, byte[] content);

    byte[] read(String key);

    void delete(String key);
}
