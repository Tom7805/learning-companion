package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.infrastructure.web.BlockedAddressException;
import com.learningcompanion.library.infrastructure.web.ReadabilityExtractor;
import com.learningcompanion.library.infrastructure.web.WebAddressPolicy;
import com.learningcompanion.library.infrastructure.web.WebFetchException;
import com.learningcompanion.library.infrastructure.web.WebPageFetcher;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import com.learningcompanion.shared.web.ClientInfo;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Thêm tài liệu từ một trang web (NCL-05-CN-004). Thứ tự kiểm: thư mục đích thuộc người học (của người khác thì 404 và
 * ghi nhật ký, TC-05) trước khi chạm tới mạng; đường dẫn hợp lệ và không trỏ vào mạng nội bộ (TC-03); lấy trang qua lớp
 * kết nối (QTN-04) rồi tách nội dung chính (TC-01). Trang chỉ có ô đăng nhập hay chặn truy cập được báo không lấy được
 * kèm lý do để giao diện gợi ý dán văn bản (TC-04). Bản chụp nội dung đi qua đúng luồng của tệp tải lên: báo trùng,
 * kiểm dung lượng, lưu mã hóa, rồi trích văn bản, chia đoạn và lập chỉ mục ở tác vụ nền.
 *
 * <p>Không mở giao dịch quanh lần lấy trang: chờ mạng không được giữ kết nối cơ sở dữ liệu.
 */
@Service
public class WebPageImportService {

    private final OwnerContext owner;
    private final FolderService folders;
    private final WebAddressPolicy policy;
    private final WebPageFetcher fetcher;
    private final ReadabilityExtractor readability;
    private final DuplicateDetector duplicates;
    private final DocumentService documentService;
    private final Clock clock;

    public WebPageImportService(OwnerContext owner, FolderService folders, WebAddressPolicy policy,
                                WebPageFetcher fetcher, ReadabilityExtractor readability,
                                DuplicateDetector duplicates, DocumentService documentService, Clock clock) {
        this.owner = owner;
        this.folders = folders;
        this.policy = policy;
        this.fetcher = fetcher;
        this.readability = readability;
        this.duplicates = duplicates;
        this.documentService = documentService;
        this.clock = clock;
    }

    public Document importPage(String url, UUID folderId, ClientInfo client) {
        UUID ownerId = owner.requireOwnerId();
        if (folderId != null) {
            folders.require(folderId, client);
        }
        URI uri = policy.parse(url);
        ReadabilityExtractor.Article article;
        URI source;
        try {
            WebPageFetcher.FetchedPage page = fetcher.fetch(uri);
            source = page.finalUri();
            article = readability.extract(page.body(), page.contentType(), page.charset(), source);
        } catch (BlockedAddressException ex) {
            // Tên miền phân giải hay chuyển hướng vào mạng nội bộ: chặn như khi dán thẳng địa chỉ nội bộ.
            throw new BusinessException(ErrorCode.WEB_ADDRESS_BLOCKED);
        } catch (WebFetchException ex) {
            throw new BusinessException(ErrorCode.WEB_PAGE_UNAVAILABLE, null, Map.of("reason", ex.reason().name()));
        }
        byte[] snapshot = article.html().getBytes(StandardCharsets.UTF_8);
        String fingerprint = Fingerprints.sha256(snapshot);
        duplicates.requireUnique(ownerId, fingerprint);
        if (folderId != null) {
            // Thư mục đích có thể đã vào thùng rác trong lúc lấy trang.
            folders.require(folderId, client);
        }
        String title = title(article.title(), source);
        return documentService.createFromWeb(ownerId, folderId, title, fileName(source), snapshot, fingerprint,
                source.toString(), clock.instant());
    }

    /** Tiêu đề tài liệu: tiêu đề bài viết, gọn trong độ dài tối đa; trang không có tiêu đề thì lấy tên miền. */
    static String title(String raw, URI source) {
        String title = LibraryNames.clean(raw == null ? "" : raw);
        if (title.isEmpty()) {
            title = LibraryNames.clean(source.getHost() == null ? "Trang web" : source.getHost());
        }
        return title.length() > DocumentService.TITLE_MAX_LENGTH
                ? title.substring(0, DocumentService.TITLE_MAX_LENGTH).strip() : title;
    }

    /** Tên tệp gốc của bản chụp: tên miền của trang. */
    static String fileName(URI source) {
        String host = source.getHost() == null ? "trang-web" : source.getHost();
        return UploadService.cleanFileName(host + ".html");
    }
}
