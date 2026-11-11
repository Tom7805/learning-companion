package com.learningcompanion.assistant.api;

import com.learningcompanion.ai.labeling.AiLabelService;
import com.learningcompanion.shared.web.ApiPaths;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thông báo đang trò chuyện với hệ thống trí tuệ nhân tạo (NCL-04-CN-005 TC-01) của chính người học đang đăng nhập:
 * đã đọc chưa, và ghi nhận đã đọc. Không có mã trên đường dẫn nên không đọc được của ai khác.
 */
@RestController
@RequestMapping(ApiPaths.ASSISTANT + "/disclosure")
public class AiDisclosureController {

    private final AiLabelService labels;

    public AiDisclosureController(AiLabelService labels) {
        this.labels = labels;
    }

    @GetMapping
    public AiLabelService.Disclosure mine() {
        return labels.disclosure();
    }

    @PostMapping
    public AiLabelService.Disclosure acknowledge() {
        return labels.acknowledge();
    }
}
