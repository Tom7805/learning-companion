package com.learningcompanion.shared.storage;

import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.security.EncryptionService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Kho tệp trên đĩa cục bộ: mỗi chủ sở hữu một thư mục, tên tệp ngẫu nhiên, nội dung mã hóa AES-GCM.
 * Khóa tệp do ứng dụng sinh ra và được kiểm định dạng chặt để không thể trỏ ra ngoài thư mục gốc.
 */
@Component
public class LocalEncryptedFileStorage implements FileStorageService {

    private static final Pattern KEY = Pattern.compile("[0-9a-f-]{36}/[0-9a-f-]{36}");

    private final Path root;
    private final EncryptionService encryption;

    public LocalEncryptedFileStorage(AppProperties properties, EncryptionService encryption) {
        this.root = Path.of(properties.storage().localRoot()).toAbsolutePath().normalize();
        this.encryption = encryption;
    }

    @Override
    public String store(UUID ownerId, byte[] content) {
        String key = ownerId + "/" + UUID.randomUUID();
        Path file = resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, encryption.encrypt(content), StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new UncheckedIOException("Không ghi được tệp vào kho", ex);
        }
        return key;
    }

    @Override
    public byte[] read(String key) {
        try {
            return encryption.decrypt(Files.readAllBytes(resolve(key)));
        } catch (IOException ex) {
            throw new UncheckedIOException("Không đọc được tệp trong kho", ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("Không xóa được tệp trong kho", ex);
        }
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Khóa tệp không hợp lệ");
        }
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Khóa tệp không hợp lệ");
        }
        return file;
    }
}
