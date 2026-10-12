package com.learningcompanion.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.storage.LocalEncryptedFileStorage;
import com.learningcompanion.shared.time.AdjustableClock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Mã hóa tệp, kho tệp và đường dẫn ký có hạn năm phút (NCL-01-CN-004). */
class FileProtectionTest {

    private static final String KEY_A = "dGVzdC1vbmx5LWVuY3J5cHRpb24ta2V5LTMyLWJ5dGU=";
    private static final String KEY_B = "dGVzdC1vbmx5LXNpZ25pbmcta2V5LW9mLTMyLWJ5dGU=";
    private static final byte[] TEXT = "Định luật Ohm: I = U / R".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path root;

    private final AdjustableClock clock =
            new AdjustableClock(Clock.fixed(Instant.parse("2026-10-12T03:00:00Z"), ZoneOffset.UTC));

    private AppProperties properties(String encryptionKey) {
        return new AppProperties("http://localhost:5173", null, null, null,
                new AppProperties.Storage(root.toString(), encryptionKey, KEY_B, Duration.ofMinutes(5)), null);
    }

    @Test
    void encryptionRoundTripsAndUsesFreshIvEachTime() {
        EncryptionService encryption = new EncryptionService(properties(KEY_A));

        byte[] first = encryption.encrypt(TEXT);
        byte[] second = encryption.encrypt(TEXT);

        assertThat(first).isNotEqualTo(second);
        assertThat(encryption.decrypt(first)).isEqualTo(TEXT);
    }

    @Test
    void tamperedCiphertextIsRejected() {
        EncryptionService encryption = new EncryptionService(properties(KEY_A));
        byte[] sealed = encryption.encrypt(TEXT);
        sealed[sealed.length - 1] ^= 1;

        assertThatThrownBy(() -> encryption.decrypt(sealed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingOrShortKeyStopsStartup() {
        assertThatThrownBy(() -> new EncryptionService(properties(""))).hasMessageContaining("encryption-key");
        assertThatThrownBy(() -> new EncryptionService(properties("c2hvcnQ="))).hasMessageContaining("32 byte");
    }

    @Test
    void storedFileIsEncryptedOnDiskAndKeptUnderTheOwnersFolder() throws Exception {
        LocalEncryptedFileStorage storage =
                new LocalEncryptedFileStorage(properties(KEY_A), new EncryptionService(properties(KEY_A)));
        UUID owner = UUID.randomUUID();

        String key = storage.store(owner, TEXT);

        assertThat(key).startsWith(owner + "/");
        byte[] onDisk = Files.readAllBytes(root.resolve(key));
        assertThat(new String(onDisk, StandardCharsets.UTF_8)).doesNotContain("Ohm");
        assertThat(storage.read(key)).isEqualTo(TEXT);
    }

    @Test
    void storageKeysCannotEscapeTheRoot() {
        LocalEncryptedFileStorage storage =
                new LocalEncryptedFileStorage(properties(KEY_A), new EncryptionService(properties(KEY_A)));

        assertThatThrownBy(() -> storage.read("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.read(UUID.randomUUID() + "/../x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signedLinkWorksForFiveMinutesThenExpires() {
        SignedUrlService signer = new SignedUrlService(properties(KEY_A), clock);
        UUID file = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        SignedUrlService.SignedLink link = signer.sign(file, owner);
        assertThat(link.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));

        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        SignedUrlService.Claims claims = signer.verify(link.token());
        assertThat(claims.objectId()).isEqualTo(file);
        assertThat(claims.ownerId()).isEqualTo(owner);

        clock.advance(Duration.ofSeconds(1));
        assertThatThrownBy(() -> signer.verify(link.token()))
                .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.FILE_LINK_EXPIRED));
    }

    @Test
    void forgedOrBrokenLinksLookLikeMissingFiles() {
        SignedUrlService signer = new SignedUrlService(properties(KEY_A), clock);
        String token = signer.sign(UUID.randomUUID(), UUID.randomUUID()).token();
        // Đổi chủ sở hữu trong phần dữ liệu nhưng giữ chữ ký cũ.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("v1." + UUID.randomUUID() + "." + UUID.randomUUID() + ".9999999999").getBytes(StandardCharsets.UTF_8));
        String forged = forgedPayload + token.substring(token.indexOf('.'));

        for (String bad : new String[] {forged, token + "x", "khong-phai-duong-dan", "", "."}) {
            assertThatThrownBy(() -> signer.verify(bad))
                    .as(bad)
                    .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        }
    }
}
