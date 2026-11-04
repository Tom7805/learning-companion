package com.learningcompanion.library.api;

import com.learningcompanion.library.api.dto.DocumentCardResponse;
import com.learningcompanion.library.api.dto.InitUploadRequest;
import com.learningcompanion.library.api.dto.UploadPartResponse;
import com.learningcompanion.library.application.UploadService;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.storage.UploadProperties;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tải tài liệu lên kho theo từng phần (NCL-05-CN-002): bắt đầu, gửi từng phần dạng nhị phân, hỏi tiến độ để tải tiếp
 * sau khi mất kết nối, hoàn tất hoặc hủy. Lần tải của người khác luôn được trả lời 404.
 */
@RestController
@RequestMapping(ApiPaths.LIBRARY + "/uploads")
public class UploadController {

    private final UploadService uploads;
    private final UploadProperties properties;

    public UploadController(UploadService uploads, UploadProperties properties) {
        this.uploads = uploads;
        this.properties = properties;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UploadPartResponse start(@Valid @RequestBody InitUploadRequest body, HttpServletRequest request) {
        return UploadPartResponse.of(uploads.start(body.fileName(), body.sizeBytes(), body.folderId(), body.sha256(),
                ClientInfo.from(request)));
    }

    @GetMapping("/{id}")
    public UploadPartResponse progress(@PathVariable UUID id, HttpServletRequest request) {
        return UploadPartResponse.of(uploads.get(id, ClientInfo.from(request)));
    }

    /** Thân yêu cầu là nội dung nhị phân của phần; đọc tối đa một phần để không ai gửi được thân khổng lồ. */
    @PutMapping(path = "/{id}/parts/{index}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public UploadPartResponse part(@PathVariable UUID id, @PathVariable int index, HttpServletRequest request)
            throws IOException {
        byte[] content;
        try (InputStream body = request.getInputStream()) {
            content = body.readNBytes(properties.chunkSizeBytes() + 1);
        }
        if (content.length > properties.chunkSizeBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "part");
        }
        return UploadPartResponse.of(uploads.receivePart(id, index, content, ClientInfo.from(request)));
    }

    @PostMapping("/{id}/complete")
    public DocumentCardResponse complete(@PathVariable UUID id, HttpServletRequest request) {
        return DocumentCardResponse.of(uploads.complete(id, ClientInfo.from(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, HttpServletRequest request) {
        uploads.cancel(id, ClientInfo.from(request));
        return ResponseEntity.noContent().build();
    }
}
