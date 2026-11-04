package com.learningcompanion.ai.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learningcompanion.ai.gateway.model.VisionRequest;
import com.learningcompanion.ai.provider.AdapterType;
import com.learningcompanion.ai.provider.ProviderCallException;
import com.learningcompanion.ai.provider.ProviderDescriptor;
import com.learningcompanion.devtools.SampleDocuments;
import com.learningcompanion.shared.storage.TestFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Bản mô phỏng nhận dạng chữ cho kiểm thử xử lý tài liệu (NCL-05-CN-003). */
class MockOcrTextTest {

    private static final ProviderDescriptor PROVIDER = new ProviderDescriptor("mock-primary", AdapterType.MOCK, null,
            null, null, null, null);

    @Test
    @DisplayName("Ảnh mẫu mang sẵn chữ cho ra đúng chữ đó; ảnh một màu không có chữ")
    void readsEmbeddedTextAndBlankImages() {
        MockAiAdapter mock = new MockAiAdapter();
        assertThat(mock.recognizeText(PROVIDER, new VisionRequest(SampleDocuments.notebook("Hóa học\n\nOxi hóa"),
                "image/png", null)).text()).isEqualTo("Hóa học\n\nOxi hóa");
        assertThat(mock.recognizeText(PROVIDER, new VisionRequest(TestFiles.png(40, 40), "image/png", null)).text())
                .isEmpty();
        assertThat(mock.recognizeText(PROVIDER, new VisionRequest(new byte[] {1, 2, 3}, "image/png", null)).text())
                .isEqualTo("Văn bản mô phỏng nhận dạng từ hình ảnh.");
    }

    @Test
    @DisplayName("Giả lập lỗi đúng số lời gọi nhận dạng chữ, đặt lại thì hết lỗi")
    void failsRequestedNumberOfCalls() {
        MockAiAdapter mock = new MockAiAdapter();
        VisionRequest request = new VisionRequest(new byte[] {1}, "image/png", null);
        mock.failNextOcr(2);
        assertThatThrownBy(() -> mock.recognizeText(PROVIDER, request)).isInstanceOf(ProviderCallException.class);
        assertThatThrownBy(() -> mock.recognizeText(PROVIDER, request)).isInstanceOf(ProviderCallException.class);
        assertThat(mock.recognizeText(PROVIDER, request).text()).isNotBlank();

        mock.failNextOcr(-1);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> mock.recognizeText(PROVIDER, request)).isInstanceOf(ProviderCallException.class);
        }
        mock.reset();
        assertThat(mock.recognizeText(PROVIDER, request).text()).isNotBlank();
    }
}
