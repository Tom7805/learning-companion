package com.learningcompanion.shared.storage;

import com.learningcompanion.shared.config.AppProperties;
import com.learningcompanion.shared.security.EncryptionService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Kho tạm cho các phần của một lần tải tệp lên (NCL-05-CN-002 TC-04). Mỗi phần là một tệp riêng đã mã hóa, đặt trong
 * thư mục của chủ sở hữu và của lần tải, nên mạng đứt giữa chừng thì các phần đã nhận vẫn còn và giao diện tải tiếp từ
 * phần thiếu. Ghép xong hoặc bỏ dở thì xóa cả thư mục.
 */
@Component
public class ChunkedUploadStore {

    private final Path root;
    private final EncryptionService encryption;

    public ChunkedUploadStore(AppProperties properties, EncryptionService encryption) {
        this.root = Path.of(properties.storage().localRoot()).toAbsolutePath().normalize().resolve("uploads");
        this.encryption = encryption;
    }

    /** Ghi một phần; ghi đè phần cùng số thứ tự nếu giao diện gửi lại sau khi mất kết nối. */
    public void writePart(UUID ownerId, UUID uploadId, int index, byte[] content) {
        Path directory = directory(ownerId, uploadId);
        try {
            Files.createDirectories(directory);
            Path temporary = directory.resolve(partName(index) + ".tmp");
            Files.write(temporary, encryption.encrypt(content));
            // Đổi tên sau khi ghi xong để phần dở dang không bao giờ bị coi là phần đã nhận.
            Files.move(temporary, directory.resolve(partName(index)), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Không ghi được phần tệp đang tải", ex);
        }
    }

    /** Ghép {@code parts} phần đầu tiên theo đúng thứ tự thành tệp hoàn chỉnh. */
    public byte[] assemble(UUID ownerId, UUID uploadId, int parts) {
        Path directory = directory(ownerId, uploadId);
        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        try {
            for (int index = 0; index < parts; index++) {
                whole.write(encryption.decrypt(Files.readAllBytes(directory.resolve(partName(index)))));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Thiếu phần tệp khi ghép", ex);
        }
        return whole.toByteArray();
    }

    public void discard(UUID ownerId, UUID uploadId) {
        Path directory = directory(ownerId, uploadId);
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Không dọn được phần tệp tạm", ex);
        }
    }

    private Path directory(UUID ownerId, UUID uploadId) {
        // Mã là UUID do máy chủ sinh ra nên không thể trỏ ra ngoài thư mục gốc.
        return root.resolve(ownerId.toString()).resolve(uploadId.toString());
    }

    private static String partName(int index) {
        return "%06d".formatted(index);
    }
}
