package com.learningcompanion.library.api;

import com.learningcompanion.library.api.dto.DocumentCardResponse;
import com.learningcompanion.library.api.dto.PasteTextRequest;
import com.learningcompanion.library.api.dto.WebImportRequest;
import com.learningcompanion.library.application.PastedTextService;
import com.learningcompanion.library.application.WebPageImportService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thêm tài liệu vào kho không qua tệp (NCL-05-CN-004): từ đường dẫn trang web hoặc từ văn bản dán vào. Tài liệu mới ở
 * trạng thái chờ xử lý và đi qua cùng tác vụ trích văn bản, chia đoạn, lập chỉ mục như tệp tải lên. Thư mục của người
 * khác luôn được trả lời 404.
 */
@RestController
@RequestMapping(ApiPaths.LIBRARY + "/sources")
public class WebImportController {

    private final WebPageImportService webPages;
    private final PastedTextService pastedTexts;

    public WebImportController(WebPageImportService webPages, PastedTextService pastedTexts) {
        this.webPages = webPages;
        this.pastedTexts = pastedTexts;
    }

    @PostMapping("/web")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentCardResponse web(@Valid @RequestBody WebImportRequest body, HttpServletRequest request) {
        return DocumentCardResponse.of(webPages.importPage(body.url(), body.folderId(), ClientInfo.from(request)));
    }

    @PostMapping("/text")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentCardResponse text(@Valid @RequestBody PasteTextRequest body, HttpServletRequest request) {
        return DocumentCardResponse.of(pastedTexts.create(body.title(), body.text(), body.folderId(),
                ClientInfo.from(request)));
    }
}
