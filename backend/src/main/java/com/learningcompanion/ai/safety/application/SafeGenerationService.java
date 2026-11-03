package com.learningcompanion.ai.safety.application;

import com.learningcompanion.ai.config.application.AiConfigResolver;
import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.gateway.AiGateway;
import com.learningcompanion.ai.gateway.model.ChatChunk;
import com.learningcompanion.ai.gateway.model.ChatRequest;
import com.learningcompanion.ai.gateway.model.ChatResult;
import com.learningcompanion.ai.quota.application.QuotaReservationService;
import com.learningcompanion.ai.quota.domain.QuotaTask;
import com.learningcompanion.ai.safety.domain.InjectionSignal;
import com.learningcompanion.ai.safety.domain.ModerationCategory;
import com.learningcompanion.ai.safety.domain.SafetyEvent;
import com.learningcompanion.ai.safety.domain.SafetyLevel;
import com.learningcompanion.shared.audit.AuditObjectType;
import com.learningcompanion.shared.web.ClientInfo;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Lối đi chung cho mọi lời gọi sinh nội dung (NCL-04-CN-004): chức năng trí tuệ nhân tạo gọi {@link #generate} thay
 * vì gọi thẳng {@code AiGateway.streamText}.
 *
 * <ol>
 *   <li>Câu hỏi qua bộ lọc đầu vào. Bị chặn thì trả lý do, không gọi mô hình và không tốn lượt. Có dấu hiệu tự gây
 *   hại thì trả câu trả lời ân cần viết sẵn kèm nguồn trợ giúp.</li>
 *   <li>Đoạn nguồn được bao trong vùng dữ liệu có đánh dấu; câu nghi chèn chỉ dẫn bị lược và đoạn đó bị gắn cờ. Lời
 *   nhắc hệ thống của phiên bản cấu hình được thêm quy tắc an toàn dữ liệu.</li>
 *   <li>Nội dung sinh ra được giữ lại tới hết câu rồi mới qua bộ lọc đầu ra và đưa cho giao diện, nên câu vi phạm
 *   không bao giờ hiện ra. Vi phạm thì phần còn lại bị dừng và kết quả là thông báo chặn.</li>
 * </ol>
 *
 * Mỗi lần can thiệp ghi sự kiện và nhật ký với loại vi phạm và mã đối tượng, không ghi nội dung.
 */
@Service
public class SafeGenerationService {

    private final ContentSafetyFilter filter;
    private final SourceDataWrapper wrapper;
    private final SelfHarmResponder selfHarm;
    private final ContentSafetyService safety;
    private final AiConfigResolver configs;
    private final AiGateway gateway;
    private final QuotaReservationService reservations;

    public SafeGenerationService(ContentSafetyFilter filter, SourceDataWrapper wrapper, SelfHarmResponder selfHarm,
                                 ContentSafetyService safety, AiConfigResolver configs, AiGateway gateway,
                                 QuotaReservationService reservations) {
        this.filter = filter;
        this.wrapper = wrapper;
        this.selfHarm = selfHarm;
        this.safety = safety;
        this.configs = configs;
        this.gateway = gateway;
        this.reservations = reservations;
    }

    /**
     * Một lời gọi sinh nội dung.
     *
     * @param actorId     người hỏi (người học, hay quản trị viên khi chạy thử); trống với tác vụ của hệ thống
     * @param feature     chức năng có cấu hình theo phiên bản
     * @param meterCode   mã ghi vào sổ đo mức sử dụng; trống thì dùng tên chức năng
     * @param config      cấu hình cụ thể (ví dụ phiên bản nháp đang thử); trống thì dùng phiên bản đang phát hành
     * @param question    câu hỏi của người dùng
     * @param sources     đoạn nguồn ghép vào ngữ cảnh
     * @param subjectType kiểu đối tượng của câu hỏi (ví dụ tin nhắn hội thoại) để ghi nhật ký khi bị chặn
     * @param subjectId   mã đối tượng đó
     * @param quotaTask   loại tác vụ tốn lượt; trống thì không đi qua sổ lượt
     */
    public record Request(UUID actorId, AiFeature feature, String meterCode, AiConfigResolver.ResolvedConfig config,
                          String question, List<SourcePassage> sources, AuditObjectType subjectType, UUID subjectId,
                          QuotaTask quotaTask, ClientInfo client) {

        public Request {
            Objects.requireNonNull(feature, "feature");
            Objects.requireNonNull(question, "question");
            sources = sources == null ? List.of() : List.copyOf(sources);
        }

        public static Request of(UUID actorId, AiFeature feature, String question) {
            return new Request(actorId, feature, null, null, question, List.of(), null, null, null, null);
        }

        public Request withSources(List<SourcePassage> value) {
            return new Request(actorId, feature, meterCode, config, question, value, subjectType, subjectId,
                    quotaTask, client);
        }

        public Request withMeterCode(String value) {
            return new Request(actorId, feature, value, config, question, sources, subjectType, subjectId,
                    quotaTask, client);
        }

        public Request withConfig(AiConfigResolver.ResolvedConfig value) {
            return new Request(actorId, feature, meterCode, value, question, sources, subjectType, subjectId,
                    quotaTask, client);
        }

        public Request withSubject(AuditObjectType type, UUID id) {
            return new Request(actorId, feature, meterCode, config, question, sources, type, id, quotaTask, client);
        }

        public Request withQuota(QuotaTask value) {
            return new Request(actorId, feature, meterCode, config, question, sources, subjectType, subjectId,
                    value, client);
        }

        public Request withClient(ClientInfo value) {
            return new Request(actorId, feature, meterCode, config, question, sources, subjectType, subjectId,
                    quotaTask, value);
        }

        String meter() {
            return meterCode == null ? feature.name() : meterCode;
        }
    }

    public enum Status { ANSWERED, BLOCKED, SUPPORT }

    public enum Stage { INPUT, OUTPUT }

    /**
     * Kết quả: câu trả lời, thông báo chặn (nhóm vi phạm và bước bị chặn) hoặc câu trả lời ân cần.
     *
     * @param flaggedSources số thứ tự các đoạn nguồn bị gắn cờ nghi chèn chỉ dẫn
     * @param result         kết quả của lớp kết nối; trống khi mô hình không được gọi
     */
    public record Outcome(Status status, Stage stage, ModerationCategory category, String text,
                          SelfHarmResponder.Reply support, List<Integer> flaggedSources, ChatResult result) {
    }

    public Outcome generate(Request request, Consumer<ChatChunk> onChunk) {
        Objects.requireNonNull(onChunk, "onChunk");
        Map<ModerationCategory, SafetyLevel> levels = safety.currentLevels();

        ModerationResult input = filter.check(request.question(), ContentSafetyFilter.Direction.INPUT, levels);
        if (input.verdict() == ModerationResult.Verdict.SUPPORT) {
            safety.record(request.actorId(), request.meter(), SafetyEvent.Kind.SELF_HARM_SUPPORT, input.category(),
                    null, request.subjectType(), request.subjectId(), request.client(), Map.of());
            return new Outcome(Status.SUPPORT, Stage.INPUT, input.category(), null,
                    selfHarm.reply(request.question()), List.of(), null);
        }
        if (input.verdict() == ModerationResult.Verdict.BLOCK) {
            safety.record(request.actorId(), request.meter(), SafetyEvent.Kind.INPUT_BLOCKED, input.category(), null,
                    request.subjectType(), request.subjectId(), request.client(), Map.of("stage", "INPUT"));
            return new Outcome(Status.BLOCKED, Stage.INPUT, input.category(), null, null, List.of(), null);
        }

        SourceDataWrapper.Wrapped wrapped = wrapper.wrap(request.sources());
        for (SourceDataWrapper.FlaggedSource flagged : wrapped.flagged()) {
            for (InjectionSignal signal : flagged.signals()) {
                safety.record(request.actorId(), request.meter(), SafetyEvent.Kind.INJECTION_FLAGGED, null, signal,
                        flagged.passage().objectType(), flagged.passage().objectId(), request.client(),
                        Map.of("sourceNumber", flagged.number()));
            }
        }
        List<Integer> flaggedNumbers = wrapped.flagged().stream().map(SourceDataWrapper.FlaggedSource::number)
                .toList();

        AiConfigResolver.ResolvedConfig config = request.config() == null
                ? configs.resolve(request.feature()) : request.config();
        String system = config.systemPrompt().strip() + "\n\n" + wrapped.guard();
        String user = wrapped.block().isEmpty() ? request.question()
                : "Nguồn:\n" + wrapped.block() + "\nCâu hỏi: " + request.question();
        ChatRequest base = config.toRequest(List.of(new ChatRequest.Message(ChatRequest.Role.USER, user)));
        ChatRequest chat = new ChatRequest(system, base.messages(), base.maxOutputUnits(), base.tuning());

        OutputGate gate = new OutputGate(levels, onChunk);
        Supplier<ChatResult> call = () -> gateway.streamText(request.meter(), chat, gate::accept);
        ChatResult result = request.quotaTask() == null || request.actorId() == null ? call.get()
                : reservations.metered(request.actorId(), request.quotaTask(), call);
        gate.finish(result.text());

        if (gate.violation != null) {
            safety.record(request.actorId(), request.meter(), SafetyEvent.Kind.OUTPUT_BLOCKED,
                    gate.violation.category(), null, request.subjectType(), request.subjectId(), request.client(),
                    Map.of("stage", "OUTPUT"));
            return new Outcome(Status.BLOCKED, Stage.OUTPUT, gate.violation.category(), null, null, flaggedNumbers,
                    result);
        }
        return new Outcome(Status.ANSWERED, null, null, gate.delivered.toString(), null, flaggedNumbers, result);
    }

    /**
     * Cổng đầu ra: gom đoạn theo luồng tới hết câu, kiểm toàn bộ phần đã có rồi mới đưa phần mới cho giao diện. Gặp vi
     * phạm thì ngừng đưa; lời gọi vẫn chạy hết để được đo như mọi lời gọi khác.
     */
    private final class OutputGate {

        private final Map<ModerationCategory, SafetyLevel> levels;
        private final Consumer<ChatChunk> downstream;
        private final StringBuilder received = new StringBuilder();
        private final StringBuilder delivered = new StringBuilder();
        private ModerationResult violation;

        OutputGate(Map<ModerationCategory, SafetyLevel> levels, Consumer<ChatChunk> downstream) {
            this.levels = levels;
            this.downstream = downstream;
        }

        void accept(ChatChunk chunk) {
            if (violation != null) {
                return;
            }
            received.append(chunk.text());
            int boundary = lastSentenceEnd(received);
            if (boundary > delivered.length()) {
                release(boundary);
            }
        }

        /** Lớp kết nối trả về toàn văn; phần chưa đưa (câu cuối không có dấu chấm) được kiểm và đưa ở đây. */
        void finish(String fullText) {
            if (violation != null) {
                return;
            }
            if (fullText != null && fullText.length() > received.length() && fullText.startsWith(received.toString())) {
                received.setLength(0);
                received.append(fullText);
            }
            release(received.length());
        }

        private void release(int upTo) {
            ModerationResult check = filter.check(received.substring(0, upTo), ContentSafetyFilter.Direction.OUTPUT,
                    levels);
            if (!check.allowed()) {
                violation = check;
                return;
            }
            String fresh = received.substring(delivered.length(), upTo);
            delivered.append(fresh);
            if (!fresh.isEmpty()) {
                downstream.accept(new ChatChunk(fresh));
            }
        }

        private int lastSentenceEnd(CharSequence text) {
            for (int i = text.length() - 1; i >= delivered.length(); i--) {
                char c = text.charAt(i);
                if (c == '.' || c == '!' || c == '?' || c == '\n') {
                    return i + 1;
                }
            }
            return -1;
        }
    }
}
