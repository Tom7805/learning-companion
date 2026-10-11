package com.learningcompanion.shared.security;

import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Đường dẫn ký có hạn để mở tệp gốc (NCL-01-CN-004): chứa mã tệp, chủ sở hữu và thời điểm hết hạn,
 * ký HMAC-SHA256. Không lưu gì phía máy chủ; ai sửa một ký tự là chữ ký sai.
 */
@Component
public class SignedUrlService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String VERSION = "v1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final Clock clock;
    private final AppProperties properties;

    public SignedUrlService(AppProperties properties, Clock clock) {
        this.key = new SecretKeySpec(EncryptionService.decodeKey(properties.storage().signingKey(), "signing-key"),
                ALGORITHM);
        this.clock = clock;
        this.properties = properties;
    }

    public SignedLink sign(UUID objectId, UUID ownerId) {
        Instant expiresAt = clock.instant().plus(properties.storage().signedUrlTtl());
        String payload = String.join(".", VERSION, objectId.toString(), ownerId.toString(),
                Long.toString(expiresAt.getEpochSecond()));
        String token = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + signature(payload);
        return new SignedLink(token, expiresAt);
    }

    /**
     * Chữ ký sai hoặc chuỗi hỏng: báo không tìm thấy như mọi tệp không tồn tại.
     * Chữ ký đúng nhưng quá hạn: báo hết hạn để giao diện tự xin đường dẫn mới.
     */
    public Claims verify(String token) {
        Claims claims = parse(token);
        if (!clock.instant().isBefore(claims.expiresAt())) {
            throw new BusinessException(ErrorCode.FILE_LINK_EXPIRED);
        }
        return claims;
    }

    private Claims parse(String token) {
        try {
            int dot = token.indexOf('.');
            if (dot <= 0) {
                throw notFound();
            }
            String payload = new String(DECODER.decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            byte[] expected = signature(payload).getBytes(StandardCharsets.US_ASCII);
            byte[] actual = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw notFound();
            }
            String[] parts = payload.split("\\.");
            if (parts.length != 4 || !VERSION.equals(parts[0])) {
                throw notFound();
            }
            return new Claims(UUID.fromString(parts[1]), UUID.fromString(parts[2]),
                    Instant.ofEpochSecond(Long.parseLong(parts[3])));
        } catch (IllegalArgumentException ex) {
            throw notFound();
        }
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không ký được đường dẫn", ex);
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }

    public record SignedLink(String token, Instant expiresAt) {
    }

    public record Claims(UUID objectId, UUID ownerId, Instant expiresAt) {
    }
}
