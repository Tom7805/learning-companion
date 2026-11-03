package com.learningcompanion.ai.safety.application;

import static com.learningcompanion.ai.safety.application.SafetyText.words;

import com.learningcompanion.ai.safety.domain.InjectionSignal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Tìm câu lệnh nằm trong đoạn nguồn (NCL-04-CN-004, QTN-16): yêu cầu bỏ qua chỉ dẫn, tiết lộ lời nhắc hệ thống, ép
 * đổi vai, hay giả dấu ranh giới vùng dữ liệu. Kiểm từng câu, để lớp bao dữ liệu chỉ lược đúng câu đó và giữ phần kiến
 * thức còn lại. Câu dặn dò bình thường trong sách ("không bỏ qua các hướng dẫn an toàn") không bị gắn cờ, vì dấu hiệu
 * đòi kèm "mọi", "trước đó", "hệ thống"... nhắm vào chính trợ lý.
 */
@Component
public class PromptInjectionDetector {

    private static final String VERB = words("bỏ qua|phớt lờ|lờ đi|quên|quên hết|quên đi|không (?:cần )?(?:tuân theo|"
            + "làm theo)|đừng (?:tuân theo|làm theo)|ghi đè|vô hiệu hóa|ignore|disregard|forget|override|bypass|"
            + "skip|bo qua");
    private static final String NOUN = words("chỉ dẫn|hướng dẫn|chỉ thị|quy tắc|lệnh|mệnh lệnh|lời nhắc|yêu cầu|"
            + "instructions?|rules?|prompts?|directives?|guidelines?|system message|chi dan|huong dan");
    private static final String QUALIFIER = words("trước|trước đó|ở trên|phía trên|ban đầu|hệ thống|của bạn|đã "
            + "(?:được )?(?:đưa|cho)|previous|prior|above|earlier|preceding|system|original|you (?:were|have been) "
            + "given|truoc do|he thong");
    private static final String ALL = words("mọi|tất cả|toàn bộ|hết thảy|all|any|every|moi|tat ca");

    private static final Map<InjectionSignal, Pattern> SIGNALS = Map.of(
            InjectionSignal.IGNORE_INSTRUCTIONS, SafetyText.compile(
                    VERB + ".{0,30}" + ALL + ".{0,20}" + NOUN
                            + "|" + VERB + ".{0,30}" + NOUN + ".{0,25}" + QUALIFIER
                            + "|" + VERB + ".{0,15}" + QUALIFIER + ".{0,15}" + NOUN),
            InjectionSignal.REVEAL_SYSTEM_PROMPT, SafetyText.compile(
                    words("tiết lộ|cho (?:tôi|tao|mình|em) (?:xem|biết)|in ra|hiển thị|nhắc lại|đọc lại|viết ra|"
                            + "chép lại|reveal|print|show|display|repeat|output|leak|tell me|dump") + ".{0,40}"
                            + words("lời nhắc hệ thống|chỉ dẫn hệ thống|câu lệnh hệ thống|prompt hệ thống|system "
                            + "prompt|system message|system instructions?|hidden instructions?|initial instructions?|"
                            + "your instructions|your (?:original |initial )?prompt|lời nhắc (?:gốc|ban đầu)|chỉ dẫn "
                            + "(?:gốc|ban đầu|ẩn)")),
            InjectionSignal.ROLE_OVERRIDE, SafetyText.compile(
                    "từ (?:giờ|bây giờ|nay)(?: trở đi)?,?\\s*(?:bạn|mày|trợ lý|ai|mô hình)\\s+(?:là|phải|sẽ|hãy|không "
                            + "còn|được phép)|bạn (?:bây giờ|giờ) là|hãy (?:đóng vai|giả vờ là|giả làm|trở thành)|"
                            + "\\byou are now\\b|\\bfrom now on,?\\s*(?:you|the assistant)\\b|\\bpretend (?:to be|you "
                            + "are)\\b|\\bact as (?:an? )?(?:unrestricted|unfiltered|jailbroken|dan)\\b|\\bdeveloper "
                            + "mode\\b|chế độ nhà phát triển|\\bjailbreak"),
            InjectionSignal.FAKE_DELIMITER, Pattern.compile(
                    "</?\\s*(?:system|source|nguon|nguồn|instructions?|assistant|user)\\s*>|\\[/?(?:system|inst)\\]|"
                            + "<<\\s*/?sys\\s*>>|<\\|(?:im_start|im_end|system|endoftext)\\|>|(?m:^\\s*#{2,}\\s*"
                            + "(?:system|instruction|chỉ dẫn))|<<<\\s*(?:nguon_du_lieu|het_nguon)",
                    SafetyText.FLAGS));

    private static final Pattern SENTENCE = Pattern.compile("[^.!?\\n]+[.!?]*\\n?|\\n");

    /** Một câu bị gắn cờ: vị trí trong đoạn gốc và kiểu dấu hiệu. */
    public record Finding(int start, int end, InjectionSignal signal) {
    }

    public record Detection(List<Finding> findings) {

        public boolean flagged() {
            return !findings.isEmpty();
        }

        public Set<InjectionSignal> signals() {
            Set<InjectionSignal> signals = new LinkedHashSet<>();
            findings.forEach(finding -> signals.add(finding.signal()));
            return signals;
        }
    }

    public Detection inspect(String text) {
        List<Finding> findings = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return new Detection(findings);
        }
        Matcher sentences = SENTENCE.matcher(text);
        while (sentences.find()) {
            String sentence = SafetyText.normalize(sentences.group());
            if (sentence.isEmpty()) {
                continue;
            }
            for (InjectionSignal signal : InjectionSignal.values()) {
                if (SIGNALS.get(signal).matcher(sentence).find()) {
                    findings.add(new Finding(sentences.start(), sentences.end(), signal));
                    break;
                }
            }
        }
        return new Detection(findings);
    }
}
