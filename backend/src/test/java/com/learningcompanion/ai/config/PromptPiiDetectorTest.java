package com.learningcompanion.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.ai.config.application.PromptPiiDetector;
import com.learningcompanion.ai.config.application.PromptPiiDetector.Finding;
import com.learningcompanion.ai.config.application.PromptPiiDetector.Kind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** NCL-04-CN-002 TC-03: lời nhắc hệ thống không được chứa địa chỉ thư hay số điện thoại. */
class PromptPiiDetectorTest {

    @Test
    @DisplayName("Đánh dấu đúng vị trí địa chỉ thư điện tử trong lời nhắc")
    void findsEmailWithPosition() {
        String prompt = "Bạn là trợ lý. Nếu cần hỗ trợ, hãy nhắn cô Lan qua lan.nguyen@truong.edu.vn nhé.";

        List<Finding> findings = PromptPiiDetector.scan(prompt);

        assertThat(findings).hasSize(1);
        Finding email = findings.getFirst();
        assertThat(email.kind()).isEqualTo(Kind.EMAIL);
        assertThat(prompt.substring(email.start(), email.end())).isEqualTo("lan.nguyen@truong.edu.vn");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0912345678", "0912 345 678", "091.234.5678", "+84 912 345 678", "+84912345678",
            "(028) 3829 5555", "+1 415 555 0132", "0084 912-345-678"})
    @DisplayName("Nhận ra số điện thoại Việt Nam và quốc tế với nhiều cách viết")
    void findsPhoneNumbers(String phone) {
        String prompt = "Gọi số " + phone + " để được giúp.";

        List<Finding> findings = PromptPiiDetector.scan(prompt);

        assertThat(findings).extracting(Finding::kind).containsExactly(Kind.PHONE);
        assertThat(prompt.substring(findings.getFirst().start(), findings.getFirst().end()).trim())
                .isEqualTo(phone);
    }

    @Test
    @DisplayName("Không bắt nhầm năm, công thức, số nguồn hay giới hạn độ dài")
    void ignoresOrdinaryNumbers() {
        String prompt = """
                Bạn là trợ lý học tập năm 2026. Ghi nguồn như [1], [12]. Công thức I = U / R, R = 220 Ω.
                Trả lời tối đa 32000 ký tự, nhiệt độ 0.30, phiên bản 2026-10-01. Ví dụ @mention hay a@b không phải \
                địa chỉ thư.""";

        assertThat(PromptPiiDetector.scan(prompt)).isEmpty();
    }

    @Test
    @DisplayName("Nhiều đoạn được trả theo thứ tự xuất hiện")
    void ordersFindings() {
        String prompt = "Liên hệ 0987654321 hoặc hotro@example.com, dự phòng +84 24 3826 1234.";

        assertThat(PromptPiiDetector.scan(prompt)).extracting(Finding::kind)
                .containsExactly(Kind.PHONE, Kind.EMAIL, Kind.PHONE);
    }
}
