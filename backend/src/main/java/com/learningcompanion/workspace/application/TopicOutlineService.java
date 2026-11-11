package com.learningcompanion.workspace.application;

import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.quota.domain.QuotaTask;
import com.learningcompanion.ai.rag.Retriever;
import com.learningcompanion.ai.safety.application.SafeGenerationService;
import com.learningcompanion.ai.safety.application.SourcePassage;
import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentChunk;
import com.learningcompanion.library.domain.DocumentChunkRepository;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.library.domain.DocumentStatus;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnershipGuard;
import com.learningcompanion.shared.web.ClientInfo;
import com.learningcompanion.workspace.domain.Topic;
import com.learningcompanion.workspace.domain.TopicRepository;
import com.learningcompanion.workspace.domain.TopicSource;
import com.learningcompanion.workspace.domain.TopicSourceRepository;
import com.learningcompanion.workspace.domain.Workspace;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dàn ý chủ đề của không gian học (NCL-06-CN-002). Người học tự lập dàn ý (không tốn lượt, TC-02) hoặc nhờ trợ lý đề
 * xuất từ các nguồn đã sẵn sàng, mỗi chủ đề kèm mục tiêu học và trích dẫn (TC-01). Đề xuất lại giữ nguyên chủ đề người
 * học đã tự thêm hay sửa tay và chỉ bổ sung chủ đề mới (TC-03). Dàn ý tối đa ba cấp (TC-04). Không gian học của người
 * khác trả 404 và lần từ chối được ghi nhật ký (TC-05).
 */
@Service
public class TopicOutlineService {

    /** Số đoạn nguồn tối đa gửi cho một lần đề xuất, chia đều giữa các tài liệu. */
    static final int MAX_PASSAGES = 24;
    private static final int EXCERPT_MAX = 2000;

    private final WorkspaceService workspaces;
    private final WorkspaceDocumentLinkService links;
    private final TopicRepository topics;
    private final TopicSourceRepository sources;
    private final DocumentChunkRepository chunks;
    private final DocumentRepository documents;
    private final SafeGenerationService generation;
    private final OwnershipGuard guard;
    private final Clock clock;

    public TopicOutlineService(WorkspaceService workspaces, WorkspaceDocumentLinkService links, TopicRepository topics,
                               TopicSourceRepository sources, DocumentChunkRepository chunks,
                               DocumentRepository documents, SafeGenerationService generation, OwnershipGuard guard,
                               Clock clock) {
        this.workspaces = workspaces;
        this.links = links;
        this.topics = topics;
        this.sources = sources;
        this.chunks = chunks;
        this.documents = documents;
        this.generation = generation;
        this.guard = guard;
        this.clock = clock;
    }

    /** Dàn ý đầy đủ: các chủ đề theo thứ tự, đoạn nguồn của từng chủ đề và tài liệu nào còn trong kho. */
    public record Outline(List<Topic> topics, Map<UUID, List<TopicSource>> sources, Set<UUID> available,
                          int readySources) {
    }

    /** Kết quả một lần đề xuất: dàn ý sau khi gộp, số chủ đề mới thêm và trạng thái của bộ lọc an toàn. */
    public record Suggestion(Outline outline, int added, SafeGenerationService.Status status) {
    }

    @Transactional(readOnly = true)
    public Outline outline(UUID workspaceId, ClientInfo client) {
        Workspace workspace = workspaces.require(workspaceId, client);
        return load(workspace, client);
    }

    @Transactional
    public Outline create(UUID workspaceId, UUID parentId, String title, String goal, ClientInfo client) {
        Workspace workspace = workspaces.require(workspaceId, client);
        List<Topic> all = list(workspace);
        if (parentId != null) {
            Topic parent = find(all, parentId);
            if (depth(parent, index(all)) >= Topic.MAX_DEPTH) {
                throw new BusinessException(ErrorCode.TOPIC_DEPTH_EXCEEDED);
            }
        }
        topics.save(Topic.create(workspace.getOwnerId(), workspace.getId(), parentId, children(all, parentId).size(),
                title.strip(), goal == null ? "" : goal.strip(), Topic.Origin.MANUAL, clock.instant()));
        return load(workspace, client);
    }

    @Transactional
    public Outline update(UUID workspaceId, UUID topicId, String title, String goal, ClientInfo client) {
        Workspace workspace = workspaces.require(workspaceId, client);
        Topic topic = requireTopic(workspace, topicId, client);
        topic.rename(title == null ? null : title.strip(), goal == null ? null : goal.strip(), clock.instant());
        topics.save(topic);
        return load(workspace, client);
    }

    /**
     * Đưa chủ đề (cùng các chủ đề con) vào dưới {@code parentId} (null là cấp gốc) ở vị trí {@code index}. Vượt ba cấp
     * thì từ chối và giữ nguyên chỗ cũ (TC-04). Đổi chỗ không tính là sửa nội dung nên không bật nhãn đã chỉnh sửa.
     */
    @Transactional
    public Outline move(UUID workspaceId, UUID topicId, UUID parentId, int index, ClientInfo client) {
        Workspace workspace = workspaces.require(workspaceId, client);
        Topic topic = requireTopic(workspace, topicId, client);
        List<Topic> all = list(workspace);
        Map<UUID, Topic> byId = index(all);
        if (parentId != null) {
            Topic parent = find(all, parentId);
            if (isWithin(parent, topic.getId(), byId)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "parentId");
            }
            if (depth(parent, byId) + height(topic, all) > Topic.MAX_DEPTH) {
                throw new BusinessException(ErrorCode.TOPIC_DEPTH_EXCEEDED);
            }
        } else if (height(topic, all) > Topic.MAX_DEPTH) {
            throw new BusinessException(ErrorCode.TOPIC_DEPTH_EXCEEDED);
        }
        Instant now = clock.instant();
        UUID oldParent = topic.getParentId();
        List<Topic> siblings = new ArrayList<>(children(all, parentId));
        siblings.remove(topic);
        siblings.add(Math.max(0, Math.min(index, siblings.size())), topic);
        topic.moveTo(parentId, 0, now);
        renumber(siblings);
        if (!Objects.equals(oldParent, parentId)) {
            List<Topic> left = new ArrayList<>(children(all, oldParent));
            left.remove(topic);
            renumber(left);
        }
        return load(workspace, client);
    }

    /** Xóa chủ đề cùng mọi chủ đề con. */
    @Transactional
    public Outline delete(UUID workspaceId, UUID topicId, ClientInfo client) {
        Workspace workspace = workspaces.require(workspaceId, client);
        Topic topic = requireTopic(workspace, topicId, client);
        List<Topic> all = list(workspace);
        removeSubtree(topic, all);
        List<Topic> siblings = new ArrayList<>(children(all, topic.getParentId()));
        siblings.remove(topic);
        renumber(siblings);
        return load(workspace, client);
    }

    /**
     * Gộp chủ đề vào một chủ đề khác: các chủ đề con và đoạn nguồn chuyển sang chủ đề đích, mục tiêu học được nối lại
     * nếu khác nhau, rồi chủ đề nguồn bị xóa. Chủ đề đích tính là đã chỉnh sửa.
     */
    @Transactional
    public Outline merge(UUID workspaceId, UUID topicId, UUID intoId, ClientInfo client) {
        Workspace workspace = workspaces.require(workspaceId, client);
        Topic topic = requireTopic(workspace, topicId, client);
        Topic into = requireTopic(workspace, intoId, client);
        List<Topic> all = list(workspace);
        Map<UUID, Topic> byId = index(all);
        if (topic.getId().equals(into.getId()) || isWithin(into, topic.getId(), byId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "intoId");
        }
        List<Topic> moving = children(all, topic.getId());
        for (Topic child : moving) {
            if (depth(into, byId) + height(child, all) > Topic.MAX_DEPTH) {
                throw new BusinessException(ErrorCode.TOPIC_DEPTH_EXCEEDED);
            }
        }
        Instant now = clock.instant();
        int next = children(all, into.getId()).size();
        for (Topic child : moving) {
            child.moveTo(into.getId(), next++, now);
            topics.save(child);
        }
        List<TopicSource> existing = sources.findAllByOwnerIdAndTopicIdIn(workspace.getOwnerId(),
                List.of(into.getId()));
        for (TopicSource source : sources.findAllByOwnerIdAndTopicIdIn(workspace.getOwnerId(),
                List.of(topic.getId()))) {
            if (existing.stream().noneMatch(source::samePassage)) {
                sources.save(source.copyTo(into.getId()));
            }
        }
        String goal = into.getGoal();
        if (!topic.getGoal().isBlank() && !goal.contains(topic.getGoal())) {
            goal = (goal.isBlank() ? topic.getGoal() : goal + " " + topic.getGoal());
            goal = goal.length() > Topic.GOAL_MAX_LENGTH ? goal.substring(0, Topic.GOAL_MAX_LENGTH) : goal;
        }
        into.rename(null, goal, now);
        topics.save(into);
        List<Topic> siblings = new ArrayList<>(children(all, topic.getParentId()));
        siblings.remove(topic);
        deleteTopic(topic);
        renumber(siblings);
        return load(workspace, client);
    }

    /**
     * Tách chủ đề: tạo chủ đề mới cùng cấp ngay sau chủ đề này, mang theo các chủ đề con được chọn. Chủ đề mới do người
     * học tạo nên không mang nhãn trí tuệ nhân tạo.
     */
    @Transactional
    public Outline split(UUID workspaceId, UUID topicId, String title, Collection<UUID> childIds, ClientInfo client) {
        Workspace workspace = workspaces.require(workspaceId, client);
        Topic topic = requireTopic(workspace, topicId, client);
        List<Topic> all = list(workspace);
        List<Topic> kids = children(all, topic.getId());
        Set<UUID> chosen = childIds == null ? Set.of() : new HashSet<>(childIds);
        if (!kids.stream().map(Topic::getId).collect(Collectors.toSet()).containsAll(chosen)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Instant now = clock.instant();
        List<Topic> siblings = new ArrayList<>(children(all, topic.getParentId()));
        Topic created = Topic.create(workspace.getOwnerId(), workspace.getId(), topic.getParentId(), 0, title.strip(),
                "", Topic.Origin.MANUAL, now);
        siblings.add(siblings.indexOf(topic) + 1, created);
        topics.save(created);
        renumber(siblings);
        int next = 0;
        List<Topic> remaining = new ArrayList<>();
        for (Topic kid : kids) {
            if (chosen.contains(kid.getId())) {
                kid.moveTo(created.getId(), next++, now);
                topics.save(kid);
            } else {
                remaining.add(kid);
            }
        }
        renumber(remaining);
        topic.markEdited(now);
        topics.save(topic);
        return load(workspace, client);
    }

    /**
     * Nhờ trợ lý đề xuất dàn ý từ các nguồn đã sẵn sàng (TC-01). Lượt được trừ qua sổ lượt; bị bộ lọc an toàn chặn thì
     * dàn ý giữ nguyên. Gộp với dàn ý đang có (TC-03): chủ đề do trợ lý đề xuất mà người học chưa sửa được thay bằng đề
     * xuất mới, chủ đề người học đã đụng tới giữ nguyên, chủ đề mới cùng tên với chủ đề đang giữ thì không thêm trùng.
     */
    @Transactional
    public Suggestion suggest(UUID workspaceId, ClientInfo client) {
        Workspace workspace = workspaces.require(workspaceId, client);
        UUID ownerId = workspace.getOwnerId();
        List<Document> ready = links.documents(workspaceId, client).stream()
                .map(WorkspaceDocumentLinkService.Attached::document)
                .filter(document -> document.getStatus() == DocumentStatus.READY).toList();
        List<Passage> passages = passages(ownerId, ready);
        if (passages.isEmpty()) {
            throw new BusinessException(ErrorCode.TOPIC_NO_READY_SOURCES);
        }
        SafeGenerationService.Outcome outcome = generation.generate(SafeGenerationService.Request
                .of(ownerId, AiFeature.TOPIC_OUTLINE,
                        "Đề xuất dàn ý chủ đề để học không gian học “" + workspace.getName() + "”.")
                .withSources(passages.stream().map(Passage::source).toList())
                .withSubject(AuditObjectType.WORKSPACE, workspace.getId())
                .withQuota(QuotaTask.TOPIC_OUTLINE)
                .withClient(client), chunk -> { });
        if (outcome.status() != SafeGenerationService.Status.ANSWERED) {
            return new Suggestion(load(workspace, client), 0, outcome.status());
        }
        List<OutlineParser.Line> lines = OutlineParser.parse(outcome.text(), passages.size());
        int added = lines.isEmpty() ? 0 : mergeSuggestion(workspace, lines, passages);
        return new Suggestion(load(workspace, client), added, outcome.status());
    }

    private int mergeSuggestion(Workspace workspace, List<OutlineParser.Line> lines, List<Passage> passages) {
        Instant now = clock.instant();
        List<Topic> all = list(workspace);
        Map<UUID, Topic> byId = index(all);
        Set<UUID> keep = new HashSet<>();
        for (Topic topic : all) {
            if (topic.ownedByLearner()) {
                for (Topic at = topic; at != null; at = at.getParentId() == null ? null : byId.get(at.getParentId())) {
                    keep.add(at.getId());
                }
            }
        }
        List<Topic> dropped = all.stream().filter(topic -> !keep.contains(topic.getId()))
                .sorted(Comparator.comparingInt((Topic topic) -> depth(topic, byId)).reversed()).toList();
        dropped.forEach(this::deleteTopic);
        List<Topic> kept = new ArrayList<>(all.stream().filter(topic -> keep.contains(topic.getId())).toList());
        renumber(children(kept, null));

        Topic[] stack = new Topic[Topic.MAX_DEPTH];
        int added = 0;
        for (OutlineParser.Line line : lines) {
            UUID parentId = line.depth() == 1 ? null : stack[line.depth() - 2] == null ? null
                    : stack[line.depth() - 2].getId();
            if (line.depth() > 1 && parentId == null) {
                continue;
            }
            String key = OutlineParser.key(line.title());
            Topic match = kept.stream().filter(topic -> Objects.equals(topic.getParentId(), parentId)
                    && OutlineParser.key(topic.getTitle()).equals(key)).findFirst().orElse(null);
            if (match == null) {
                match = Topic.create(workspace.getOwnerId(), workspace.getId(), parentId,
                        children(kept, parentId).size(), line.title(), line.goal(), Topic.Origin.AI, now);
                topics.save(match);
                kept.add(match);
                added++;
                for (int number : line.sources()) {
                    Passage passage = passages.get(number - 1);
                    DocumentChunk chunk = passage.chunk();
                    String excerpt = chunk.getContent().length() > EXCERPT_MAX
                            ? chunk.getContent().substring(0, EXCERPT_MAX) : chunk.getContent();
                    sources.save(TopicSource.of(workspace.getOwnerId(), match.getId(), passage.document().getId(),
                            passage.document().getTitle(), chunk.getLocation(), passage.passage(), excerpt));
                }
            }
            stack[line.depth() - 1] = match;
            for (int deeper = line.depth(); deeper < Topic.MAX_DEPTH; deeper++) {
                stack[deeper] = null;
            }
        }
        return added;
    }

    /** Một đoạn nguồn gửi cho mô hình, kèm tài liệu và thứ tự đoạn theo cách đánh số của khung xem. */
    private record Passage(Document document, DocumentChunk chunk, int passage, SourcePassage source) {
    }

    /** Lấy đều các đoạn của mỗi tài liệu (đầu, giữa, cuối), tổng không quá {@link #MAX_PASSAGES}. */
    private List<Passage> passages(UUID ownerId, List<Document> ready) {
        List<Passage> picked = new ArrayList<>();
        if (ready.isEmpty()) {
            return picked;
        }
        int perDocument = Math.max(2, MAX_PASSAGES / ready.size());
        for (Document document : ready) {
            List<DocumentChunk> all = chunks.findAllByOwnerIdAndDocumentIdOrderByOrdinalAsc(ownerId,
                    document.getId());
            if (all.isEmpty()) {
                continue;
            }
            int take = Math.min(perDocument, all.size());
            Set<Integer> indexes = new java.util.TreeSet<>();
            for (int i = 0; i < take; i++) {
                indexes.add(take == 1 ? 0 : (int) Math.round((double) i * (all.size() - 1) / (take - 1)));
            }
            for (int i : indexes) {
                if (picked.size() >= MAX_PASSAGES) {
                    return picked;
                }
                DocumentChunk chunk = all.get(i);
                picked.add(new Passage(document, chunk, Retriever.passageOf(chunk, all),
                        new SourcePassage(document.getTitle(), chunk.getContent(), AuditObjectType.DOCUMENT,
                                document.getId())));
            }
        }
        return picked;
    }

    private Outline load(Workspace workspace, ClientInfo client) {
        UUID ownerId = workspace.getOwnerId();
        List<Topic> all = list(workspace);
        Map<UUID, List<TopicSource>> grouped = new LinkedHashMap<>();
        if (!all.isEmpty()) {
            for (TopicSource source : sources.findAllByOwnerIdAndTopicIdIn(ownerId,
                    all.stream().map(Topic::getId).toList())) {
                grouped.computeIfAbsent(source.getTopicId(), key -> new ArrayList<>()).add(source);
            }
        }
        Set<UUID> cited = grouped.values().stream().flatMap(List::stream).map(TopicSource::getDocumentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> available = cited.isEmpty() ? Set.of() : documents.findAllByOwnerIdAndIdIn(ownerId, cited).stream()
                .filter(document -> !document.isTrashed()).map(Document::getId).collect(Collectors.toSet());
        int ready = (int) links.documents(workspace.getId(), client).stream()
                .filter(item -> item.document().getStatus() == DocumentStatus.READY).count();
        return new Outline(all, grouped, available, ready);
    }

    private List<Topic> list(Workspace workspace) {
        return new ArrayList<>(topics.findAllByOwnerIdAndWorkspaceIdOrderByPositionAsc(workspace.getOwnerId(),
                workspace.getId()));
    }

    private Topic requireTopic(Workspace workspace, UUID topicId, ClientInfo client) {
        Topic topic = guard.require(topics, topicId, AuditObjectType.TOPIC, client);
        if (!topic.getWorkspaceId().equals(workspace.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return topic;
    }

    private static Topic find(List<Topic> all, UUID id) {
        return all.stream().filter(topic -> topic.getId().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static Map<UUID, Topic> index(List<Topic> all) {
        Map<UUID, Topic> byId = new HashMap<>();
        all.forEach(topic -> byId.put(topic.getId(), topic));
        return byId;
    }

    private static List<Topic> children(List<Topic> all, UUID parentId) {
        return all.stream().filter(topic -> Objects.equals(topic.getParentId(), parentId))
                .sorted(Comparator.comparingInt(Topic::getPosition)).toList();
    }

    /** Cấp của chủ đề: 1 là cấp gốc. */
    static int depth(Topic topic, Map<UUID, Topic> byId) {
        int depth = 1;
        for (UUID parent = topic.getParentId(); parent != null && byId.containsKey(parent);
             parent = byId.get(parent).getParentId()) {
            depth++;
        }
        return depth;
    }

    /** Số cấp của cây con tính từ chủ đề (chủ đề lá có chiều cao 1). */
    static int height(Topic topic, List<Topic> all) {
        int deepest = 0;
        for (Topic child : children(all, topic.getId())) {
            deepest = Math.max(deepest, height(child, all));
        }
        return deepest + 1;
    }

    /** {@code candidate} là chính {@code ancestorId} hoặc nằm trong cây con của nó. */
    private static boolean isWithin(Topic candidate, UUID ancestorId, Map<UUID, Topic> byId) {
        for (Topic at = candidate; at != null; at = at.getParentId() == null ? null : byId.get(at.getParentId())) {
            if (at.getId().equals(ancestorId)) {
                return true;
            }
        }
        return false;
    }

    private void renumber(List<Topic> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            Topic topic = ordered.get(i);
            if (topic.getPosition() != i) {
                topic.place(i);
                topics.save(topic);
            }
        }
    }

    private void removeSubtree(Topic topic, List<Topic> all) {
        for (Topic child : children(all, topic.getId())) {
            removeSubtree(child, all);
        }
        deleteTopic(topic);
    }

    private void deleteTopic(Topic topic) {
        for (TopicSource source : sources.findAllByOwnerIdAndTopicIdIn(topic.getOwnerId(), List.of(topic.getId()))) {
            sources.delete(source);
        }
        topics.delete(topic);
    }
}
