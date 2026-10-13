package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.shared.export.DataExportContributor;
import com.learningcompanion.shared.export.ExportFormat;
import com.learningcompanion.shared.export.ExportWriter;
import com.learningcompanion.shared.storage.FileStorageService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phần "Thư viện" của gói xuất: tệp gốc của từng tài liệu, nội dung văn bản đã trích ở dạng Markdown và bảng kê.
 * Tệp gốc nằm dưới "files/" theo cây thư mục của thư viện; khi chức năng thư mục (NCL-03-CN-001) có, đường dẫn
 * thư mục được ghép vào trước tên tệp ở đây.
 */
@Component
public class LibraryExportContributor implements DataExportContributor {

    private final DocumentRepository documents;
    private final FileStorageService storage;

    public LibraryExportContributor(DocumentRepository documents, FileStorageService storage) {
        this.documents = documents;
        this.storage = storage;
    }

    @Override
    public String section() {
        return "library";
    }

    @Override
    public String title() {
        return "Thư viện tài liệu";
    }

    @Override
    public List<String> describe() {
        return List.of(
                "files/: tệp gốc của từng tài liệu, theo cây thư mục trong thư viện.",
                "text/: nội dung văn bản đã trích từ từng tài liệu, mỗi tài liệu một tệp Markdown.",
                "documents.json: bảng kê tài liệu (tên, loại, kích thước, thời điểm) và đường dẫn tới tệp tương ứng.");
    }

    @Override
    public List<ExportFormat> formats() {
        return List.of(ExportFormat.ORIGINAL, ExportFormat.MARKDOWN, ExportFormat.JSON);
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(UUID ownerId, ExportWriter writer) {
        List<Document> mine = documents.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .sorted(Comparator.comparing(Document::getCreatedAt))
                .toList();
        if (mine.isEmpty()) {
            return;
        }
        List<Map<String, Object>> index = new ArrayList<>();
        for (Document document : mine) {
            String file = writer.bytes(ExportWriter.path("files", document.getOriginalFilename()),
                    storage.read(document.getStorageKey()));
            String text = document.getContentText().isBlank() ? null
                    : writer.text(ExportWriter.path("text", document.getTitle() + ".md"), markdown(document, file));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", document.getId());
            entry.put("title", document.getTitle());
            entry.put("type", document.getType().name());
            entry.put("originalFilename", document.getOriginalFilename());
            entry.put("mimeType", document.getMimeType());
            entry.put("sizeBytes", document.getSizeBytes());
            entry.put("createdAt", document.getCreatedAt());
            entry.put("updatedAt", document.getUpdatedAt());
            entry.put("file", file);
            entry.put("text", text);
            index.add(entry);
        }
        writer.json("documents.json", index);
    }

    private static String markdown(Document document, String file) {
        return "# " + document.getTitle().strip() + "\n\n"
                + "- Loại: " + document.getType().name() + "\n"
                + "- Tệp gốc: ../" + file + "\n"
                + "- Thêm vào thư viện: " + document.getCreatedAt() + "\n\n"
                + "---\n\n"
                + document.getContentText().strip() + "\n";
    }
}
