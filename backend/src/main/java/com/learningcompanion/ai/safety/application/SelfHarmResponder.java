package com.learningcompanion.ai.safety.application;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Câu trả lời ân cần cho câu hỏi có dấu hiệu tự gây hại (NCL-04-CN-004 TC-03): ghi nhận cảm xúc, khuyên tìm người
 * giúp ngay khi gặp nguy hiểm và giới thiệu nguồn trợ giúp, thay vì từ chối cụt. Câu trả lời viết sẵn, không gọi mô
 * hình, nên không phụ thuộc nhà cung cấp và luôn giống nhau.
 */
@Component
public class SelfHarmResponder {

    /** Một nguồn trợ giúp; {@code contact} là số gọi được, để trống khi là người ở quanh người học. */
    public record Resource(String name, String contact, String note) {
    }

    public record Reply(String language, String message, List<Resource> resources) {
    }

    private static final Pattern VIETNAMESE = Pattern.compile(
            "[ăâđêôơưáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ENGLISH = Pattern.compile(
            "\\b(i|me|my|want|die|myself|life|live|suicide|kill|hurt)\\b", Pattern.CASE_INSENSITIVE);

    private static final Reply VI = new Reply("vi", """
            Mình rất tiếc khi bạn đang phải trải qua cảm giác nặng nề như vậy, và cảm ơn bạn đã nói ra. Bạn không \
            phải một mình đối mặt với chuyện này. Nếu bạn đang có ý định làm hại bản thân hoặc đang gặp nguy hiểm, \
            hãy gọi ngay 115 hoặc nhờ một người ở gần giúp đỡ. Bạn cũng có thể kể với người thân, thầy cô hay một \
            người bạn tin tưởng: chia sẻ thường giúp lòng nhẹ hơn. Khi nào bạn sẵn sàng, mình vẫn ở đây để cùng \
            bạn học tiếp.""", List.of(
            new Resource("Cấp cứu", "115", "Gọi ngay khi bạn hoặc ai đó đang gặp nguy hiểm."),
            new Resource("Tổng đài quốc gia bảo vệ trẻ em", "111", "Miễn phí, 24/7, dành cho người dưới 18 tuổi."),
            new Resource("Người bạn tin tưởng", null,
                    "Người thân, thầy cô chủ nhiệm hoặc chuyên viên tư vấn tâm lý học đường.")));

    private static final Reply EN = new Reply("en", """
            I'm really sorry you're feeling this way, and thank you for telling me. You don't have to face this \
            alone. If you are thinking about hurting yourself or you are in danger right now, please call your \
            local emergency number (115 in Vietnam) or ask someone near you for help. Talking to a family member, \
            a teacher or a friend you trust can also help. Whenever you're ready, I'm still here to study with you.\
            """, List.of(
            new Resource("Emergency", "115", "Call right away if you or someone else is in danger."),
            new Resource("National child protection hotline (Vietnam)", "111", "Free, 24/7, for people under 18."),
            new Resource("Someone you trust", null, "A family member, your homeroom teacher or a school counsellor.")));

    public Reply reply(String question) {
        String text = question == null ? "" : question;
        return !VIETNAMESE.matcher(text).find() && ENGLISH.matcher(text).find() ? EN : VI;
    }
}
