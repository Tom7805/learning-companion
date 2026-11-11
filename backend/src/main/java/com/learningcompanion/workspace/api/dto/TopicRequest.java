package com.learningcompanion.workspace.api.dto;

import com.learningcompanion.workspace.domain.Topic;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Yêu cầu thay đổi dàn ý chủ đề (NCL-06-CN-002). */
public final class TopicRequest {

    private TopicRequest() {
    }

    /** Thêm chủ đề do người học tự lập; {@code parentId} trống là chủ đề cấp gốc. */
    public record Create(@NotBlank @Size(max = Topic.TITLE_MAX_LENGTH) String title,
                         @Size(max = Topic.GOAL_MAX_LENGTH) String goal, UUID parentId) {
    }

    /** Sửa tên hay mục tiêu học; trường để trống giữ nguyên. */
    public record Update(@Size(min = 1, max = Topic.TITLE_MAX_LENGTH) String title,
                         @Size(max = Topic.GOAL_MAX_LENGTH) String goal) {
    }

    /** Đưa chủ đề vào dưới {@code parentId} (trống là cấp gốc) tại vị trí {@code index}. */
    public record Move(UUID parentId, int index) {
    }

    /** Gộp chủ đề vào chủ đề {@code intoId}. */
    public record Merge(UUID intoId) {
    }

    /** Tách thành chủ đề mới cùng cấp, mang theo các chủ đề con được chọn. */
    public record Split(@NotBlank @Size(max = Topic.TITLE_MAX_LENGTH) String title, List<UUID> childIds) {
    }
}
