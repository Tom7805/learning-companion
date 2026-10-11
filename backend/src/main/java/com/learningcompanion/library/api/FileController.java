package com.learningcompanion.library.api;

import com.learningcompanion.library.application.DocumentService;
import com.learningcompanion.shared.web.ApiPaths;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kho tệp: chỉ mở tệp qua đường dẫn ký còn hạn. Quá hạn trả 410 FILE_LINK_EXPIRED,
 * chữ ký sai trả 404 như tệp không tồn tại.
 */
@RestController
@RequestMapping(ApiPaths.FILES)
public class FileController {

    private final DocumentService documents;

    public FileController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/{token}")
    public ResponseEntity<byte[]> open(@PathVariable String token,
                                       @RequestParam(defaultValue = "false") boolean download) {
        DocumentService.OpenedFile file = documents.open(token);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.document().getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.document().getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                // Tệp do người dùng đưa lên không bao giờ được chạy như trang web của ứng dụng.
                .header("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'")
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .body(file.content());
    }
}
