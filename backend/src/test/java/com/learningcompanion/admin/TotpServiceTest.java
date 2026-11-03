package com.learningcompanion.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.admin.application.TotpService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Mã sáu chữ số theo thời gian (NCL-03-CN-002), đối chiếu với bộ mẫu chuẩn của RFC 6238. */
class TotpServiceTest {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private final TotpService totp = new TotpService();

    @Test
    void matchesRfc6238Vectors() {
        // RFC 6238 phụ lục B (8 chữ số); sáu chữ số cuối là mã của ứng dụng sinh mã.
        assertThat(totp.codeAt(RFC_SECRET, TotpService.stepAt(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(totp.codeAt(RFC_SECRET, TotpService.stepAt(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(totp.codeAt(RFC_SECRET, TotpService.stepAt(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    void acceptsOneStepOfClockDriftEachWay() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = TotpService.stepAt(now);
        assertThat(totp.verify(RFC_SECRET, totp.codeAt(RFC_SECRET, step - 1), now, 0)).hasValue(step - 1);
        assertThat(totp.verify(RFC_SECRET, totp.codeAt(RFC_SECRET, step + 1), now, 0)).hasValue(step + 1);
        assertThat(totp.verify(RFC_SECRET, totp.codeAt(RFC_SECRET, step - 2), now, 0)).isEmpty();
    }

    @Test
    void rejectsReusedCodeMalformedInputAndWrongCode() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = TotpService.stepAt(now);
        String code = totp.codeAt(RFC_SECRET, step);
        assertThat(totp.verify(RFC_SECRET, code, now, step)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, "12345", now, 0)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, "abcdef", now, 0)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, null, now, 0)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, code.substring(0, 3) + " " + code.substring(3), now, 0)).hasValue(step);
    }

    @Test
    void provisioningUriCarriesIssuerAndBase32Secret() {
        assertThat(TotpService.base32(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(totp.provisioningUri("Learning Companion", "quan.tri@example.com", RFC_SECRET))
                .isEqualTo("otpauth://totp/Learning%20Companion:quan.tri%40example.com"
                        + "?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Learning%20Companion"
                        + "&algorithm=SHA1&digits=6&period=30");
    }
}
