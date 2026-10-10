package com.learningcompanion.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learningcompanion.auth.application.BreachedPasswordChecker;
import com.learningcompanion.auth.application.PasswordPolicyValidator;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator(new BreachedPasswordChecker());

    @Test
    void acceptsLongUncommonPassphrase() {
        assertThatCode(() -> validator.validate("song-xanh-mua-thu-2026")).doesNotThrowAnyException();
    }

    @Test
    void acceptsExactlyTenCharacters() {
        assertThatCode(() -> validator.validate("k7#vQ2!mZp")).doesNotThrowAnyException();
    }

    @Test
    void rejectsNineCharacters() {
        assertThatThrownBy(() -> validator.validate("k7#vQ2!mZ"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertCode(ex, ErrorCode.PASSWORD_TOO_SHORT));
    }

    @Test
    void acceptsVietnamesePassphrase() {
        assertThatCode(() -> validator.validate("mùa thu Hà Nội đẹp")).doesNotThrowAnyException();
    }

    @Test
    void countsCharactersNotUtf16Units() {
        // 5 biểu tượng cảm xúc chiếm 10 đơn vị UTF-16 nhưng chỉ là 5 ký tự.
        assertThatThrownBy(() -> validator.validate("😀😀😀😀😀"))
                .satisfies(ex -> assertCode(ex, ErrorCode.PASSWORD_TOO_SHORT));
    }

    @Test
    void rejectsOverlyLongPassword() {
        assertThatThrownBy(() -> validator.validate("a".repeat(129)))
                .satisfies(ex -> assertCode(ex, ErrorCode.PASSWORD_TOO_LONG));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password1234", "Password1234", "QWERTYUIOP", "1234567890", "matkhau123"})
    void rejectsBreachedPasswordsIgnoringCase(String password) {
        assertThatThrownBy(() -> validator.validate(password))
                .satisfies(ex -> assertCode(ex, ErrorCode.PASSWORD_BREACHED));
    }

    @Test
    void rejectsMissingPassword() {
        assertThatThrownBy(() -> validator.validate(null))
                .satisfies(ex -> assertCode(ex, ErrorCode.PASSWORD_TOO_SHORT));
    }

    private static void assertCode(Throwable ex, ErrorCode code) {
        BusinessException business = (BusinessException) ex;
        org.assertj.core.api.Assertions.assertThat(business.code()).isEqualTo(code);
        org.assertj.core.api.Assertions.assertThat(business.field()).isEqualTo("password");
    }
}
