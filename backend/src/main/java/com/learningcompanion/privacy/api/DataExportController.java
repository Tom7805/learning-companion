package com.learningcompanion.privacy.api;

import com.learningcompanion.privacy.api.dto.DataExportDownloadRequest;
import com.learningcompanion.privacy.api.dto.DataExportOverviewResponse;
import com.learningcompanion.privacy.api.dto.DataExportResponse;
import com.learningcompanion.privacy.application.DataExportService;
import com.learningcompanion.shared.security.AuthenticatedUser;
import com.learningcompanion.shared.security.SignedUrlService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Xuất toàn bộ dữ liệu học tập (NCL-01-CN-008). Chỉ chính chủ xem và tải được gói của mình; mã gói của người khác
 * được trả lời như không tồn tại.
 */
@RestController
@RequestMapping(ApiPaths.EXPORTS)
public class DataExportController {

    private final DataExportService exports;

    public DataExportController(DataExportService exports) {
        this.exports = exports;
    }

    @GetMapping
    public DataExportOverviewResponse overview(@AuthenticationPrincipal AuthenticatedUser user) {
        return DataExportOverviewResponse.of(exports.overview(user));
    }

    /** Nhận yêu cầu và dựng gói ở luồng nền; thư báo được gửi khi gói sẵn sàng. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DataExportOverviewResponse request(@AuthenticationPrincipal AuthenticatedUser user,
                                              HttpServletRequest http) {
        return DataExportOverviewResponse.of(exports.request(user, ClientInfo.from(http)));
    }

    @GetMapping("/{id}")
    public DataExportResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                  HttpServletRequest http) {
        return DataExportResponse.of(exports.get(id, ClientInfo.from(http)), !exports.recentlySignedIn(user));
    }

    /** Xác nhận lại danh tính rồi cấp đường dẫn tải sống năm phút. */
    @PostMapping("/{id}/download")
    public DownloadLinkResponse download(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                         @Valid @RequestBody DataExportDownloadRequest request,
                                         HttpServletRequest http) {
        SignedUrlService.SignedLink link = exports.download(user, id, request.password(), ClientInfo.from(http));
        return new DownloadLinkResponse(ApiPaths.EXPORTS + "/files/" + link.token(), link.expiresAt());
    }

    /** Đường dẫn ký tự nó là quyền tải, nên điểm này không cần phiên (trình duyệt tải tệp như một trang thường). */
    @GetMapping("/files/{token}")
    public ResponseEntity<byte[]> file(@PathVariable String token) {
        DataExportService.Download download = exports.open(token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .body(download.content());
    }

    public record DownloadLinkResponse(String url, Instant expiresAt) {
    }
}
