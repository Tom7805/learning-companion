package com.learningcompanion.devtools;

import com.learningcompanion.assistant.domain.Conversation;
import com.learningcompanion.assistant.domain.ConversationRepository;
import com.learningcompanion.assistant.domain.Message;
import com.learningcompanion.assistant.domain.MessageRepository;
import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ApiPaths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dựng sẵn hội thoại riêng ở nhiều thời điểm cho môi trường thử (NCL-07-CN-003 TC-01: lịch sử chia nhóm hôm nay, hôm
 * qua, bảy ngày, cũ hơn). Mỗi hội thoại có một câu hỏi và một câu trả lời đã xong. Không bao giờ được bật ở môi trường
 * thật.
 */
@RestController
@RequestMapping(ApiPaths.DEV + "/assistant/conversations")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevConversationController {

    private static final int MAX = 200;

    private final UserAccountRepository accounts;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final Clock clock;

    public DevConversationController(UserAccountRepository accounts, ConversationRepository conversations,
                                     MessageRepository messages, Clock clock) {
        this.accounts = accounts;
        this.conversations = conversations;
        this.messages = messages;
        this.clock = clock;
    }

    @PostMapping("/seed")
    @Transactional
    public SeedResponse seed(@RequestBody SeedRequest request) {
        UserAccount account = accounts.findByEmail(request.email() == null ? ""
                        : request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<UUID> created = new ArrayList<>();
        List<Item> items = request.items() == null ? List.of() : request.items();
        for (Item item : items.subList(0, Math.min(items.size(), MAX))) {
            Instant at = clock.instant().minus(Duration.ofHours(Math.max(0, item.hoursAgo())));
            Conversation conversation = Conversation.start(account.getId(), null, item.question(), at);
            conversation.pin(item.pinned(), at);
            conversations.save(conversation);
            messages.save(Message.question(account.getId(), conversation.getId(), item.question(), List.of(), at));
            Message answer = Message.answer(account.getId(), conversation.getId(), at.plusMillis(1));
            answer.finish(Message.Status.COMPLETE, item.answer() == null ? "Câu trả lời mẫu." : item.answer(),
                    List.of(), null, at.plusMillis(2));
            messages.save(answer);
            created.add(conversation.getId());
        }
        return new SeedResponse(created);
    }

    /** Một hội thoại mẫu: câu hỏi (cũng là tên), câu trả lời, cách đây bao nhiêu giờ và có ghim không. */
    public record Item(String question, String answer, long hoursAgo, boolean pinned) {
    }

    public record SeedRequest(String email, List<Item> items) {
    }

    public record SeedResponse(List<UUID> conversations) {
    }
}
