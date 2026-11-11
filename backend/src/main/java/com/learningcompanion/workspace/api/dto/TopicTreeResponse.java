package com.learningcompanion.workspace.api.dto;

import com.learningcompanion.workspace.application.TopicOutlineService;
import com.learningcompanion.workspace.domain.Topic;
import com.learningcompanion.workspace.domain.TopicSource;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Dàn ý chủ đề dạng cây (NCL-06-CN-002). Mỗi chủ đề có nhãn do trí tuệ nhân tạo sinh ra khi do trợ lý đề xuất (kèm cờ
 * đã chỉnh sửa), số tài liệu nguồn liên quan và các đoạn nguồn để mở đúng chỗ ở khung xem.
 *
 * @param readySources số tài liệu sẵn sàng của không gian học, để giao diện biết đề xuất dàn ý được hay chưa
 * @param added        số chủ đề mới thêm ở lần đề xuất này (chỉ có khi trả lời yêu cầu đề xuất)
 * @param safety       bộ lọc an toàn chặn yêu cầu đề xuất (BLOCKED hay SUPPORT); trống khi bình thường
 */
public record TopicTreeResponse(List<Node> topics, int readySources, Integer added, String safety) {

    public record Node(UUID id, UUID parentId, String title, String goal, boolean aiGenerated, boolean edited,
                       int sourceCount, List<Source> sources, List<Node> children) {
    }

    /** Một đoạn nguồn của chủ đề; {@code available} sai khi tài liệu đã bị xóa. */
    public record Source(UUID documentId, String documentTitle, Location location, int passage, String excerpt,
                         boolean available) {
    }

    public record Location(String kind, int number, int end) {
    }

    public static TopicTreeResponse of(TopicOutlineService.Outline outline) {
        return new TopicTreeResponse(nodes(outline, null), outline.readySources(), null, null);
    }

    public static TopicTreeResponse of(TopicOutlineService.Suggestion suggestion) {
        String safety = suggestion.status() == com.learningcompanion.ai.safety.application.SafeGenerationService
                .Status.ANSWERED ? null : suggestion.status().name();
        return new TopicTreeResponse(nodes(suggestion.outline(), null), suggestion.outline().readySources(),
                suggestion.added(), safety);
    }

    private static List<Node> nodes(TopicOutlineService.Outline outline, UUID parentId) {
        Map<UUID, List<TopicSource>> sources = outline.sources();
        Set<UUID> available = outline.available();
        return outline.topics().stream().filter(topic -> Objects.equals(topic.getParentId(), parentId))
                .sorted(Comparator.comparingInt(Topic::getPosition))
                .map(topic -> {
                    List<Source> cited = sources.getOrDefault(topic.getId(), List.of()).stream()
                            .map(source -> new Source(source.getDocumentId(), source.getDocumentTitle(),
                                    new Location(source.getLocation().kind().name(), source.getLocation().number(),
                                            source.getLocation().end()),
                                    source.getPassage(), source.getExcerpt(),
                                    source.getDocumentId() != null && available.contains(source.getDocumentId())))
                            .toList();
                    int documents = (int) cited.stream().map(source -> source.documentId() != null
                            ? source.documentId().toString() : source.documentTitle()).distinct().count();
                    return new Node(topic.getId(), topic.getParentId(), topic.getTitle(), topic.getGoal(),
                            topic.getOrigin() == Topic.Origin.AI, topic.isEdited(), documents, cited,
                            nodes(outline, topic.getId()));
                }).toList();
    }
}
