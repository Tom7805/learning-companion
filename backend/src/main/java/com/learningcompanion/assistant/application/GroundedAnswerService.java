package com.learningcompanion.assistant.application;

import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.quota.domain.QuotaTask;
import com.learningcompanion.ai.rag.Retriever;
import com.learningcompanion.ai.safety.application.SafeGenerationService;
import com.learningcompanion.ai.safety.application.SourcePassage;
import com.learningcompanion.assistant.domain.Citation;
import com.learningcompanion.assistant.domain.Message;
import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentStatus;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

/**
 * Câu trả lời có căn cứ trên tài liệu đang chọn (NCL-07-CN-001, QTN-21, QTN-22):
 *
 * <ol>
 *   <li>Chưa chọn tài liệu nào thì nhắc chọn nguồn; không có đoạn nào liên quan thì nói rõ không tìm thấy và gợi ý tài
 *   liệu khác của kho có nói về câu hỏi. Cả hai trường hợp đều không gọi mô hình và không tốn lượt.</li>
 *   <li>Có đoạn liên quan thì gửi qua lối sinh nội dung chung (bộ lọc an toàn, lớp bao dữ liệu, cấu hình đang phát
 *   hành, sổ lượt: một câu hỏi một lượt, lỗi thì hoàn lượt).</li>
 *   <li>Câu trả lời hiện dần; khối câu hỏi gợi ý ở cuối được tách riêng. Lưu kèm các đoạn được trích theo số
 *   trích dẫn trong câu trả lời.</li>
 * </ol>
 */
@Service
public class GroundedAnswerService {

    static final int EXCERPT_MAX = 1200;
    static final int ELSEWHERE_LIMIT = 3;

    private final Retriever retriever;
    private final SafeGenerationService generation;
    private final ConversationService conversations;
    /** Câu trả lời đang sinh: người học bấm dừng thì phần còn lại không được đưa ra nữa. */
    private final Map<UUID, AtomicBoolean> running = new ConcurrentHashMap<>();

    public GroundedAnswerService(Retriever retriever, SafeGenerationService generation,
                                 ConversationService conversations) {
        this.retriever = retriever;
        this.generation = generation;
        this.conversations = conversations;
    }

    /** Nơi nhận kết quả của một lần trả lời, theo thứ tự: các đoạn chữ, rồi đúng một lần kết thúc. */
    public interface Listener {

        void chunk(String text);

        void safety(SafeGenerationService.Outcome outcome);

        void done(Answer answer);

        void failed(String code, String message, String partial);
    }

    /** Kết quả cuối của câu trả lời. */
    public record Answer(Message.Status status, String text, List<String> suggestions, List<Citation> citations,
                         List<Document> elsewhere, List<Integer> flaggedSources) {
    }

    /** Trả lời một lượt hỏi đã được nhận; chạy trên luồng nền, không có phiên đăng nhập. */
    public void answer(ConversationService.Turn turn, ClientInfo client, Listener listener) {
        UUID ownerId = turn.conversation().getOwnerId();
        UUID answerId = turn.answer().getId();
        AtomicBoolean stopped = new AtomicBoolean(false);
        running.put(answerId, stopped);
        StringBuilder shown = new StringBuilder();
        try {
            List<Document> ready = turn.scope().stream()
                    .filter(document -> document.getStatus() == DocumentStatus.READY).toList();
            if (!turn.selectedAny() || turn.scope().isEmpty()) {
                finish(ownerId, answerId, new Answer(Message.Status.NO_SOURCES, "", List.of(), List.of(), List.of(),
                        List.of()), listener);
                return;
            }
            String question = turn.question().getContent();
            List<Retriever.Hit> hits = retriever.retrieve(ownerId, ready.stream().map(Document::getId).toList(),
                    question);
            if (hits.isEmpty()) {
                List<Document> elsewhere = retriever.elsewhere(ownerId,
                        turn.scope().stream().map(Document::getId).toList(), question, ELSEWHERE_LIMIT);
                finish(ownerId, answerId, new Answer(Message.Status.NOT_FOUND, "", List.of(), List.of(), elsewhere,
                        List.of()), listener);
                return;
            }

            AnswerSplitter splitter = new AnswerSplitter(text -> {
                if (!stopped.get()) {
                    shown.append(text);
                    listener.chunk(text);
                }
            });
            List<SourcePassage> sources = hits.stream().map(hit -> new SourcePassage(sourceTitle(hit),
                    hit.chunk().getContent(), AuditObjectType.DOCUMENT, hit.document().getId())).toList();
            SafeGenerationService.Outcome outcome = generation.generate(SafeGenerationService.Request
                    .of(ownerId, AiFeature.ASSISTANT_CHAT, question)
                    .withSources(sources)
                    .withSubject(AuditObjectType.CONVERSATION, turn.conversation().getId())
                    .withQuota(QuotaTask.ASSISTANT_QUESTION)
                    .withClient(client), chunk -> splitter.accept(chunk.text()));
            splitter.finish();

            if (outcome.status() != SafeGenerationService.Status.ANSWERED) {
                listener.safety(outcome);
                Message.Status status = outcome.status() == SafeGenerationService.Status.SUPPORT
                        ? Message.Status.SUPPORT : Message.Status.BLOCKED;
                finish(ownerId, answerId, new Answer(status, "", List.of(), List.of(), List.of(),
                        outcome.flaggedSources()), listener);
                return;
            }
            boolean wasStopped = stopped.get();
            String text = wasStopped ? shown.toString().strip() : splitter.shown();
            List<String> suggestions = wasStopped ? List.of() : suggestions(splitter.suggestions(), hits);
            List<Citation> cited = citations(ownerId, answerId, text, hits);
            finish(ownerId, answerId, new Answer(wasStopped ? Message.Status.STOPPED : Message.Status.COMPLETE, text,
                    suggestions, cited, List.of(), outcome.flaggedSources()), listener);
        } catch (BusinessException ex) {
            // Dịch vụ mô hình lỗi giữa chừng (lượt đã được hoàn) hay hết lượt: giữ phần đã hiện (TC-05).
            String partial = shown.toString().strip();
            conversations.complete(ownerId, answerId, Message.Status.FAILED, partial, List.of(), ex.code().name(),
                    List.of());
            listener.failed(ex.code().name(), ex.getMessage(), partial);
        } catch (RuntimeException ex) {
            String partial = shown.toString().strip();
            conversations.complete(ownerId, answerId, Message.Status.FAILED, partial, List.of(),
                    com.learningcompanion.shared.exception.ErrorCode.INTERNAL_ERROR.name(), List.of());
            listener.failed(com.learningcompanion.shared.exception.ErrorCode.INTERNAL_ERROR.name(),
                    com.learningcompanion.shared.exception.ErrorCode.INTERNAL_ERROR.defaultMessage(), partial);
        } finally {
            running.remove(answerId);
        }
    }

    /** Người học bấm dừng: phần còn lại không hiện nữa, câu trả lời lưu phần đã hiện. Trả về false nếu không còn chạy. */
    public boolean stop(UUID answerId) {
        AtomicBoolean flag = running.get(answerId);
        if (flag == null) {
            return false;
        }
        flag.set(true);
        return true;
    }

    private void finish(UUID ownerId, UUID answerId, Answer answer, Listener listener) {
        conversations.complete(ownerId, answerId, answer.status(), answer.text(), answer.suggestions(), null,
                answer.citations());
        listener.done(answer);
    }

    /** Tiêu đề nguồn gửi cho mô hình: tên tài liệu kèm vị trí, ví dụ "Bài giảng cây nhị phân · Trang 8". */
    static String sourceTitle(Retriever.Hit hit) {
        ChunkLocation location = hit.chunk().getLocation();
        String where = switch (location.kind()) {
            case PAGE -> "Trang " + location.number();
            case SLIDE -> "Trang trình chiếu " + location.number();
            case IMAGE -> "Ảnh " + location.number();
            case PARAGRAPH -> location.end() > location.number()
                    ? "Đoạn " + location.number() + "–" + location.end() : "Đoạn " + location.number();
        };
        return hit.document().getTitle() + " · " + where;
    }

    private static List<Citation> citations(UUID ownerId, UUID answerId, String text, List<Retriever.Hit> hits) {
        Set<Integer> numbers = CitationNumbers.in(text, hits.size());
        List<Citation> cited = new ArrayList<>();
        for (int number : numbers) {
            Retriever.Hit hit = hits.get(number - 1);
            String content = hit.chunk().getContent();
            cited.add(Citation.of(ownerId, answerId, number, hit.document().getId(), hit.chunk().getId(),
                    hit.document().getTitle(), hit.chunk().getLocation(), hit.passage(),
                    content.length() > EXCERPT_MAX ? content.substring(0, EXCERPT_MAX) + "…" : content));
        }
        return cited;
    }

    /** Ba câu hỏi gợi ý: lấy của mô hình, thiếu thì bổ sung câu theo tài liệu được trích nhiều nhất. */
    static List<String> suggestions(List<String> fromModel, List<Retriever.Hit> hits) {
        List<String> result = new ArrayList<>(fromModel);
        String title = hits.get(0).document().getTitle();
        List<String> fallback = List.of("Tóm tắt các ý chính của “" + title + "”.",
                "Cho một ví dụ minh họa trong “" + title + "”.",
                "Khái niệm nào trong “" + title + "” quan trọng nhất?");
        for (String candidate : fallback) {
            if (result.size() >= 3) {
                break;
            }
            if (!result.contains(candidate)) {
                result.add(candidate);
            }
        }
        return List.copyOf(result.subList(0, 3));
    }
}
