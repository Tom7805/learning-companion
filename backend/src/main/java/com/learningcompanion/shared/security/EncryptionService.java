package com.learningcompanion.shared.security;

import com.learningcompanion.shared.config.AppProperties;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Mã hóa tệp gốc trước khi ghi vào kho (AES-256-GCM). Mỗi tệp dùng một IV ngẫu nhiên riêng đặt ở đầu
 * dữ liệu; thẻ xác thực của GCM phát hiện mọi chỉnh sửa trên tệp đã lưu.
 */
@Component
public class EncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public EncryptionService(AppProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.storage().encryptionKey(), "encryption-key"), "AES");
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain);
            return ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không mã hóa được tệp", ex);
        }
    }

    public byte[] decrypt(byte[] stored) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
            return cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Tệp đã lưu bị hỏng hoặc sai khóa", ex);
        }
    }

    /** Khóa 32 byte dạng Base64; thiếu hoặc sai độ dài thì ứng dụng dừng ngay lúc khởi động. */
    static byte[] decodeKey(String base64, String name) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("Thiếu app.storage." + name);
        }
        byte[] bytes = Base64.getDecoder().decode(base64.trim());
        if (bytes.length != 32) {
            throw new IllegalStateException("app.storage." + name + " phải dài đúng 32 byte");
        }
        return bytes;
    }
}
