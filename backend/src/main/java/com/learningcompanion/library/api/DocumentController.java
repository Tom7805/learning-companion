package com.learningcompanion.library.api;

import com.learningcompanion.library.api.dto.DocumentCardResponse;
import com.learningcompanion.library.api.dto.DocumentResponse;
import com.learningcompanion.library.application.DocumentService;
import com.learningcompanion.shared.security.SignedUrlService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tài liệu của người học đang đăng nhập; tài liệu của người khác luôn được trả lời 404. */
@RestController
@RequestMapping(ApiPaths.LIBRARY + "/documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping
    public List<DocumentCardResponse> list() {
        return documents.list().stream().map(DocumentCardResponse::of).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id, HttpServletRequest request) {
        return DocumentResponse.of(documents.get(id, ClientInfo.from(request)));
    }

    /** Xin đường dẫn mở tệp gốc mới; giao diện gọi lại khi đường dẫn cũ hết hạn. */
    @PostMapping("/{id}/file-link")
    public FileLinkResponse fileLink(@PathVariable UUID id, HttpServletRequest request) {
        SignedUrlService.SignedLink link = documents.fileLink(id, ClientInfo.from(request));
        return new FileLinkResponse(ApiPaths.FILES + "/" + link.token(), link.expiresAt());
    }

    public record FileLinkResponse(String url, Instant expiresAt) {
    }
}
