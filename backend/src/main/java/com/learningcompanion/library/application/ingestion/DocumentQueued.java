package com.learningcompanion.library.application.ingestion;

import java.util.UUID;

/** Một tài liệu vừa vào hàng chờ xử lý (tải lên xong hoặc người học bấm thử lại); tác vụ nền nhận sau khi lưu xong. */
public record DocumentQueued(UUID documentId, UUID ownerId) {
}
