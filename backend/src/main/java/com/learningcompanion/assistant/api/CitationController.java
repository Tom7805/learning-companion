package com.learningcompanion.assistant.api;

import com.learningcompanion.assistant.api.dto.CitationResponse;
import com.learningcompanion.assistant.application.CitationService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Đoạn gốc của một trích dẫn cho khung xem nhanh (NCL-07-CN-002); của người khác thì 404 và ghi nhật ký. */
@RestController
@RequestMapping(ApiPaths.ASSISTANT + "/citations")
public class CitationController {

    private final CitationService citations;

    public CitationController(CitationService citations) {
        this.citations = citations;
    }

    @GetMapping("/{id}")
    public CitationResponse preview(@PathVariable UUID id, HttpServletRequest request) {
        CitationService.Preview preview = citations.preview(id, ClientInfo.from(request));
        return CitationResponse.of(preview.citation(), preview.available(), preview.highlight());
    }
}
