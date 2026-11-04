package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.domain.DocumentChunk;

/**
 * Một đoạn đã lập chỉ mục: vị trí trong tài liệu gốc (loại, số bắt đầu, số kết thúc), đề mục gần nhất, nội dung và
 * nguồn (lớp chữ của tệp hay nhận dạng chữ). Không kèm vector.
 */
public record DocumentChunkResponse(int ordinal, Location location, String heading, String content, String source) {

    public record Location(String kind, int number, int end) {
    }

    public static DocumentChunkResponse of(DocumentChunk chunk) {
        return new DocumentChunkResponse(chunk.getOrdinal(), new Location(chunk.getLocation().kind().name(),
                chunk.getLocation().number(), chunk.getLocation().end()), chunk.getHeading(), chunk.getContent(),
                chunk.getSource().name());
    }
}
