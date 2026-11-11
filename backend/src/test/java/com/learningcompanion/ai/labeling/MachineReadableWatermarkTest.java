package com.learningcompanion.ai.labeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MachineReadableWatermarkTest {

    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final Instant AT = Instant.parse("2026-11-11T03:00:00Z");

    private final MachineReadableWatermark watermark = new MachineReadableWatermark();

    @Test
    @DisplayName("đính dấu ai_generated vào khối siêu dữ liệu đầu tệp, đọc lại được, nội dung giữ nguyên")
    void appliesReadableMarker() {
        String file = watermark.apply("Duyệt \"cây\"\nnhị phân", "Nội dung trả lời.",
                AiProvenance.generated(ID, "ASSISTANT_CHAT", AT));

        assertThat(file).startsWith("---\ntitle: \"Duyệt \\\"cây\\\" nhị phân\"\nai_generated: true\n");
        assertThat(file).contains("ai_feature: ASSISTANT_CHAT\n", "content_id: " + ID + "\n",
                "generated_at: 2026-11-11T03:00:00Z\n", "edited: false\n").doesNotContain("edited_at");
        assertThat(file).endsWith("---\nNội dung trả lời.\n");
        assertThat(watermark.verify(file)).isTrue();
    }

    @Test
    @DisplayName("nội dung đã sửa tay giữ dấu và ghi thêm đã chỉnh sửa")
    void editedContentKeepsMarker() {
        String file = watermark.apply("Ghi chú", "Đã sửa.",
                new AiProvenance(ID, "ASSISTANT_CHAT", AT, AT.plusSeconds(60)));

        assertThat(file).contains("ai_generated: true\n", "edited: true\n", "edited_at: 2026-11-11T03:01:00Z\n");
        assertThat(watermark.verify(file)).isTrue();
    }

    @Test
    @DisplayName("tệp không có dấu, hay nội dung bị đổi sau khi đính, thì không qua được kiểm tra")
    void verifyRejectsMissingOrTampered() {
        String file = watermark.apply("Ghi chú", "Nội dung gốc.", AiProvenance.generated(ID, "ASSISTANT_CHAT", AT));

        assertThat(watermark.verify("# Ghi chú\n\nNội dung gốc.\n")).isFalse();
        assertThat(watermark.verify(file.replace("Nội dung gốc.", "Nội dung khác."))).isFalse();
        assertThat(watermark.verify(file.replace("ai_generated: true", "ai_generated: false"))).isFalse();
    }

    @Test
    @DisplayName("giả lập lỗi: đúng số lần được đặt thì ném lỗi, sau đó chạy lại bình thường")
    void simulatedFailures() {
        AiProvenance provenance = AiProvenance.generated(ID, "ASSISTANT_CHAT", AT);
        watermark.simulateFailures(2);

        assertThatThrownBy(() -> watermark.apply("A", "B", provenance))
                .isInstanceOf(MachineReadableWatermark.WatermarkException.class);
        assertThatThrownBy(() -> watermark.apply("A", "B", provenance))
                .isInstanceOf(MachineReadableWatermark.WatermarkException.class);
        assertThat(watermark.verify(watermark.apply("A", "B", provenance))).isTrue();

        watermark.simulateFailures(-1);
        assertThatThrownBy(() -> watermark.apply("A", "B", provenance))
                .isInstanceOf(MachineReadableWatermark.WatermarkException.class);
        watermark.reset();
        assertThat(watermark.apply("A", "B", provenance)).contains("ai_generated: true");
    }
}
