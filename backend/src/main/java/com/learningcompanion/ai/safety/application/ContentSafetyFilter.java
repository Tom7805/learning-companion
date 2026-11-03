package com.learningcompanion.ai.safety.application;

import static com.learningcompanion.ai.safety.application.SafetyText.compile;
import static com.learningcompanion.ai.safety.application.SafetyText.words;

import com.learningcompanion.ai.safety.domain.ModerationCategory;
import com.learningcompanion.ai.safety.domain.SafetyLevel;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Bộ lọc nội dung không an toàn (NCL-04-CN-004, QTN-15): kiểm câu hỏi trước khi gửi cho mô hình và nội dung sinh ra
 * trước khi hiển thị, theo các nhóm bạo lực, thù ghét, tình dục, hướng dẫn gây hại và lừa đảo.
 *
 * <p>Bộ lọc chạy tại máy chủ, không gọi dịch vụ ngoài, nên không làm lộ câu hỏi và cho cùng kết quả ở mọi lần chạy.
 * Mỗi dấu hiệu có mức độ: 1 là nhắc tới (chỉ ngưỡng nghiêm mới chặn), 2 là yêu cầu rõ, 3 là yêu cầu trắng trợn (mọi
 * ngưỡng đều chặn). Dấu hiệu đòi ngữ cảnh đi kèm (cách làm, đối tượng là người) để câu hỏi học tập như lịch sử chiến
 * tranh hay lệnh {@code kill} trong tin học vẫn được trả lời.
 *
 * <p>Câu hỏi có dấu hiệu tự gây hại không bị chặn cụt mà trả về {@link ModerationResult.Verdict#SUPPORT}, ở mọi ngưỡng.
 */
@Component
public class ContentSafetyFilter {

    /** Văn bản đang kiểm là câu hỏi của người học hay nội dung mô hình sinh ra. */
    public enum Direction { INPUT, OUTPUT }

    private record Rule(ModerationCategory category, int severity, Direction only, Pattern pattern, Pattern unless) {

        boolean appliesTo(Direction direction) {
            return only == null || only == direction;
        }

        boolean matches(String text) {
            return pattern.matcher(text).find() && (unless == null || !unless.matcher(text).find());
        }
    }

    // ------------------------------------------------------------------ từ khóa dùng chung

    private static final String HOW_TO = words("cách|cách nào|làm sao|làm thế nào|hướng dẫn|chỉ (?:tôi|mình|em|tao|"
            + "tớ|con)|công thức|các bước|quy trình|bày (?:tôi|mình|em|tao)|how (?:do i|to|can i|would i|could i)|"
            + "instructions?|steps?|guide|recipe|tutorial|teach me|show me");
    private static final String MAKE = words("chế tạo|chế|tự chế|làm|tạo|sản xuất|lắp ráp|lắp|điều chế|tổng hợp|"
            + "pha chế|build|make|assemble|craft|synthesi[sz]e|manufacture|cook|produce|che tao|tu che");
    private static final String WEAPON = words("bom|bom xăng|mìn|lựu đạn|thuốc nổ|chất nổ|vũ khí|súng|đạn|chất độc|"
            + "khí độc|sarin|ricin|ma túy|ma tuý|heroin|thuốc lắc|bombs?|explosives?|weapons?|guns?|firearms?|"
            + "grenades?|landmines?|molotov|nerve agents?|poison gas|meth(?:amphetamine)?|cocaine|vu khi|thuoc no|"
            + "chat no|ma tuy|luu dan");
    private static final String VIOLENT = words("giết|sát hại|đầu độc|ám sát|hành hạ|tra tấn|đánh chết|đâm chết|"
            + "bắn chết|kill|murder|poison|assassinate|torture|stab|shoot");
    private static final String PERSON = words("người|ai đó|một người|bạn (?:tôi|mình|cùng lớp|học)|thầy|cô giáo|"
            + "bố|mẹ|vợ|chồng|hàng xóm|đứa|thằng|con bé|someone|somebody|a person|people|my (?:teacher|friend|"
            + "classmate|neighbou?r|wife|husband|mom|dad|father|mother|brother|sister)|him|her|them");
    private static final String GROUP = words("người|dân tộc|sắc tộc|chủng tộc|tôn giáo|dân|bọn|lũ|tụi|people|race|"
            + "ethnic|jews|muslims|christians|immigrants|refugees|blacks|whites|asians|gays|women|men|nhập cư");
    private static final String DEHUMANIZE = words("súc vật|sâu bọ|rác rưởi|ký sinh trùng|hạ đẳng|thấp kém|"
            + "không phải là người|animals|vermin|subhuman|parasites|inferior|trash|cockroaches|rats");
    private static final String MINOR = words("trẻ em|trẻ con|trẻ vị thành niên|bé gái|bé trai|học sinh tiểu học|"
            + "học sinh cấp hai|child|children|minors?|underage|kids?|preteens?|1[0-5] tuổi|1[0-5][- ]year[- ]old");
    private static final String SEXUAL = words("tình dục|khiêu dâm|làm tình|quan hệ tình dục|khỏa thân|khoả thân|"
            + "sex|sexual|porn|pornographic|nude|naked|erotic");
    private static final String FRAUD_VERB = words("lừa đảo|lừa tiền|lừa|chiếm đoạt|phishing|scam|đánh cắp|ăn cắp|"
            + "trộm|hack|steal|làm giả|giả mạo|counterfeit|forge|fake|rửa tiền|launder");
    private static final String FRAUD_OBJECT = words("tiền|tài khoản|mật khẩu|thẻ|ngân hàng|giấy tờ|bằng cấp|bằng đại "
            + "học|bằng lái|căn cước|cccd|hộ chiếu|chữ ký|người già|người khác|mã otp|otp|money|accounts?|passwords?|"
            + "credentials|credit cards?|bank|documents?|ids?|passports?|signatures?|diplomas?|someone|victims?|"
            + "elderly");
    private static final String FIRST_PERSON = words("tôi|mình|tao|tớ|em|con|i");

    private static final Pattern PREVENTION = compile(words("phòng chống|phòng tránh|bảo vệ|nhận biết|tố giác|"
            + "báo cáo|xâm hại|prevent|prevention|protect|report|awareness|safeguarding"));

    /** Câu hỏi học tập về tư tưởng phân biệt trong lịch sử (vì sao phát xít tuyên truyền...), không phải lời thù ghét. */
    private static final Pattern HISTORY = compile(words("tuyên truyền|cho rằng|bị coi|bị xem|xem là|coi là|tin rằng|"
            + "quan niệm|tư tưởng|vì sao|tại sao|propaganda|claimed|believed|ideology|why did|why were"));

    private static final List<Rule> RULES = List.of(
            // Hướng dẫn gây hại
            rule(ModerationCategory.HARMFUL_INSTRUCTIONS, 3, null,
                    HOW_TO + ".{0,40}" + MAKE + ".{0,40}" + WEAPON),
            rule(ModerationCategory.HARMFUL_INSTRUCTIONS, 3, null,
                    MAKE + ".{0,25}" + WEAPON + ".{0,40}" + words("tại nhà|ở nhà|at home|từng bước|step by step|"
                            + "chi tiết|từ đồ|từ vật liệu|in detail")),
            rule(ModerationCategory.HARMFUL_INSTRUCTIONS, 3, Direction.OUTPUT,
                    words("bước \\d+|step \\d+") + ".{0,80}" + WEAPON),
            rule(ModerationCategory.HARMFUL_INSTRUCTIONS, 2, null,
                    words("tự chế|tự làm|homemade|diy|home-made") + ".{0,12}" + WEAPON),
            rule(ModerationCategory.HARMFUL_INSTRUCTIONS, 1, null, MAKE + ".{0,15}" + WEAPON),
            // Bạo lực
            rule(ModerationCategory.VIOLENCE, 3, null, HOW_TO + ".{0,40}" + VIOLENT + ".{0,30}" + PERSON),
            rule(ModerationCategory.VIOLENCE, 2, null, FIRST_PERSON + "\\s+" + words("sẽ|muốn|định|will|want to|"
                    + "am going to|gonna") + "\\s+" + VIOLENT + ".{0,20}" + words("nó|hắn|mày|chúng nó|bọn|cả lớp|"
                    + "mọi người|thầy|cô|you|him|her|them|everyone")),
            rule(ModerationCategory.VIOLENCE, 1, null, words("máu me|phanh thây|xẻ thịt|moi ruột|gore|dismember|"
                    + "disembowel|behead")),
            // Thù ghét
            rule(ModerationCategory.HATE, 3, null, words("tiêu diệt|diệt sạch|giết hết|thanh trừng|đuổi hết|"
                    + "exterminate|wipe out|kill all|eradicate|get rid of all") + ".{0,30}" + GROUP),
            new Rule(ModerationCategory.HATE, 3, null, compile(words("bọn|lũ|tụi|những người|người|all|those|these")
                    + ".{1,40}?" + words("là|đều là|are|is") + "\\s+(?:lũ |bọn |đồ |những |a |an )?" + DEHUMANIZE),
                    HISTORY),
            new Rule(ModerationCategory.HATE, 3, null, compile(words("chủng tộc|dân tộc|race|races") + ".{0,20}"
                    + words("thấp kém|hạ đẳng|thượng đẳng|inferior|superior")), HISTORY),
            new Rule(ModerationCategory.HATE, 3, null, compile(words("inferior|superior") + "\\s+"
                    + words("race|races")), HISTORY),
            rule(ModerationCategory.HATE, 2, null, words("ghét|căm ghét|hate") + ".{0,12}" + words("tất cả|hết|all|"
                    + "mọi") + ".{0,12}" + GROUP),
            rule(ModerationCategory.HATE, 1, null, words("đồ|lũ|bọn|thằng") + "\\s+" + words("mọi rợ|man di|óc chó|"
                    + "rác rưởi|súc vật")),
            // Tình dục
            new Rule(ModerationCategory.SEXUAL, 3, null,
                    compile(MINOR + ".{0,40}" + SEXUAL + "|" + SEXUAL + ".{0,40}" + MINOR), PREVENTION),
            rule(ModerationCategory.SEXUAL, 2, null, words("viết|kể|tả|mô tả|tạo|sáng tác|write|describe|generate|"
                    + "tell|roleplay") + ".{0,30}" + words("truyện|cảnh|câu chuyện|story|stories|scene|roleplay|chat")
                    + ".{0,30}" + words("sex|tình dục|khiêu dâm|làm tình|erotic|porn|18\\+")),
            rule(ModerationCategory.SEXUAL, 2, null, words("ảnh|hình|video|clip|photos?|pictures?|images?|videos?")
                    + "\\s+" + words("khỏa thân|khoả thân|nude|naked|porn|sex|khiêu dâm")),
            rule(ModerationCategory.SEXUAL, 1, null, words("khiêu dâm|porn|pornography|nsfw|hentai")),
            // Lừa đảo
            rule(ModerationCategory.FRAUD, 3, null, HOW_TO + ".{0,40}" + FRAUD_VERB + ".{0,40}" + FRAUD_OBJECT),
            rule(ModerationCategory.FRAUD, 3, null, words("viết|soạn|tạo|làm|write|draft|create|build") + ".{0,30}"
                    + words("email|tin nhắn|thư|trang web|website|message|page|sms") + ".{0,20}"
                    + words("lừa đảo|giả mạo|phishing|scam")),
            rule(ModerationCategory.FRAUD, 2, null, FIRST_PERSON + "\\s+" + words("muốn|cần|định|want to|need to|"
                    + "will|am going to") + ".{0,20}" + FRAUD_VERB + ".{0,30}" + FRAUD_OBJECT),
            rule(ModerationCategory.FRAUD, 1, null, words("lừa đảo|chiếm đoạt|đánh cắp|làm giả|steal|forge|"
                    + "counterfeit") + ".{0,15}" + FRAUD_OBJECT),
            // Nội dung sinh ra cổ vũ tự gây hại luôn bị chặn
            rule(ModerationCategory.SELF_HARM, 3, Direction.OUTPUT, words("hãy|nên|cứ|cứ việc|you should|just|go")
                    + "\\s+" + words("tự tử|tự sát|chết đi|kill yourself|end your life")));

    /** Dấu hiệu người học muốn tự gây hại; luôn dẫn tới câu trả lời ân cần thay vì từ chối. */
    private static final List<Pattern> SELF_HARM = List.of(
            compile(FIRST_PERSON + "\\s+(?:thật sự |thực sự |rất |cứ |chỉ |đang |cũng |really |just )?"
                    + words("muốn|định|sẽ|nghĩ đến|nghĩ tới|tính|sắp|want to|wanna|going to|am going to|"
                    + "thinking about|plan to|planning to") + "\\s+" + words("chết|tự tử|tự sát|tự vẫn|kết liễu|"
                    + "tự làm hại|tự làm đau|tự rạch|die|kill myself|end my life|end it all|hurt myself|"
                    + "cut myself|commit suicide")),
            compile(FIRST_PERSON + ".{0,25}" + words("tự tử|tự sát|tự hại|rạch tay|cắt tay|suicide|suicidal|"
                    + "self[- ]harm")),
            compile(words("kill myself|end my life|không muốn sống|không thiết sống|chán sống|không còn lý do để sống|"
                    + "không còn lý do sống|sống không để làm gì|no reason to live|don'?t want to live|want to die|"
                    + "khong muon song|chan song|muon chet")),
            compile(words("cách|làm sao|how to|ways to|cach") + ".{0,20}" + words("tự tử|tự sát|kill myself|"
                    + "commit suicide|chết nhanh|chết không đau|tu sat")));

    private static Rule rule(ModerationCategory category, int severity, Direction only, String regex) {
        return new Rule(category, severity, only, compile(regex), null);
    }

    /**
     * Kiểm một đoạn văn bản theo ngưỡng của từng nhóm. Nhóm thiếu trong {@code levels} dùng ngưỡng tiêu chuẩn.
     */
    public ModerationResult check(String text, Direction direction, Map<ModerationCategory, SafetyLevel> levels) {
        String normalized = SafetyText.normalize(text);
        if (normalized.isEmpty()) {
            return ModerationResult.ALLOWED;
        }
        if (direction == Direction.INPUT && SELF_HARM.stream().anyMatch(p -> p.matcher(normalized).find())) {
            return new ModerationResult(ModerationResult.Verdict.SUPPORT, ModerationCategory.SELF_HARM, 3);
        }
        Rule strongest = null;
        for (Rule rule : RULES) {
            if (!rule.appliesTo(direction) || (strongest != null && strongest.severity() >= rule.severity())) {
                continue;
            }
            SafetyLevel level = rule.category().configurable()
                    ? levels.getOrDefault(rule.category(), SafetyLevel.STANDARD) : SafetyLevel.STRICT;
            if (level.blocks(rule.severity()) && rule.matches(normalized)) {
                strongest = rule;
            }
        }
        return strongest == null ? ModerationResult.ALLOWED
                : new ModerationResult(ModerationResult.Verdict.BLOCK, strongest.category(), strongest.severity());
    }
}
