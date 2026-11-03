package com.learningcompanion.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Mã khôi phục gõ tay: không phân biệt hoa thường, bỏ qua khoảng trắng và dấu gạch (NCL-03-CN-002 TC-03). */
class RecoveryCodeServiceTest {

    @Test
    void normalizesHowPeopleTypeRecoveryCodes() {
        assertThat(RecoveryCodeService.normalize("ABCDE-FGHJK")).isEqualTo("abcde-fghjk");
        assertThat(RecoveryCodeService.normalize(" abcde fghjk ")).isEqualTo("abcde-fghjk");
        assertThat(RecoveryCodeService.normalize("abcdefghjk")).isEqualTo("abcde-fghjk");
        assertThat(RecoveryCodeService.normalize(null)).isEmpty();
        assertThat(RecoveryCodeService.normalize("ngan")).isEqualTo("ngan");
    }
}
