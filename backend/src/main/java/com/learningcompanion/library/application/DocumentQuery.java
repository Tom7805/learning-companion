package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.DocumentStatus;
import com.learningcompanion.library.domain.DocumentType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Điều kiện xem danh sách trong kho tài liệu (NCL-05-CN-001): phạm vi (cả kho, một thư mục, gần đây, có gắn sao), bộ
 * lọc theo loại tệp, nhãn, trạng thái xử lý, khoảng ngày thêm và cách sắp xếp. Nhiều nhãn: tài liệu phải có đủ mọi nhãn.
 */
public record DocumentQuery(Scope scope, UUID folderId, Set<DocumentType> types, Set<UUID> tagIds,
                            Set<DocumentStatus> statuses, Instant addedFrom, Instant addedTo, Sort sort,
                            Direction direction) {

    /** "Gần đây" chỉ giữ ngần này tài liệu mở hoặc thêm gần nhất. */
    public static final int RECENT_LIMIT = 30;

    public DocumentQuery {
        scope = scope == null ? Scope.ALL : scope;
        types = types == null ? Set.of() : Set.copyOf(types);
        tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        sort = sort == null ? Sort.ADDED : sort;
        direction = direction == null ? (sort == Sort.NAME ? Direction.ASC : Direction.DESC) : direction;
    }

    public static DocumentQuery all() {
        return new DocumentQuery(Scope.ALL, null, null, null, null, null, null, null, null);
    }

    public enum Scope {
        /** Mọi tài liệu ở mọi thư mục. */
        ALL,
        /** Tài liệu nằm trực tiếp trong một thư mục; mã thư mục rỗng là thư mục gốc. */
        FOLDER,
        /** Tài liệu mở hoặc thêm gần nhất. */
        RECENT,
        STARRED
    }

    public enum Sort { NAME, ADDED, OPENED, SIZE }

    public enum Direction { ASC, DESC }
}
