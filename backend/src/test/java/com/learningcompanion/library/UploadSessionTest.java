package com.learningcompanion.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.library.domain.UploadSession;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NCL-05-CN-002 TC-04: tiến độ tải theo phần, gửi lại phần đã có không làm sai tiến độ. */
class UploadSessionTest {

    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

    @Test
    @DisplayName("Tệp năm byte, mỗi phần hai byte: ba phần, phần cuối một byte")
    void splitsFileIntoParts() {
        UploadSession session = UploadSession.start(UUID.randomUUID(), null, "a.pdf", 5, 2, NOW, NOW);
        assertThat(session.partCount()).isEqualTo(3);
        assertThat(session.expectedLength(0)).isEqualTo(2);
        assertThat(session.expectedLength(2)).isEqualTo(1);
    }

    @Test
    @DisplayName("Mất kết nối rồi gửi lại phần vừa gửi: tiến độ không tăng hai lần, tải tiếp từ phần còn thiếu")
    void resendingAPartDoesNotAdvanceTwice() {
        UploadSession session = UploadSession.start(UUID.randomUUID(), null, "a.pdf", 5, 2, NOW, NOW);
        session.acceptPart(0, NOW, NOW);
        session.acceptPart(0, NOW, NOW);
        assertThat(session.getReceivedBytes()).isEqualTo(2);
        assertThat(session.nextPart()).isEqualTo(1);
        session.acceptPart(1, NOW, NOW);
        session.acceptPart(2, NOW, NOW);
        assertThat(session.isFullyReceived()).isTrue();
        // Phần cuối ngắn hơn cỡ phần: nhận đủ thì không còn phần nào thiếu.
        assertThat(session.nextPart()).isEqualTo(session.partCount());
        session.acceptPart(2, NOW, NOW);
        assertThat(session.getReceivedBytes()).isEqualTo(5);
    }
}
