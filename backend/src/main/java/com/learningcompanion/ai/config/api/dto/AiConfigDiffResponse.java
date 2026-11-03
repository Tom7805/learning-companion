package com.learningcompanion.ai.config.api.dto;

import com.learningcompanion.ai.config.application.ConfigDiffService;
import java.util.List;

/** Khác biệt giữa hai phiên bản: tham số đổi giá trị và lời nhắc hệ thống theo từng dòng. */
public record AiConfigDiffResponse(int fromVersion, int toVersion, List<FieldChange> fields,
                                   List<ConfigDiffService.Line> promptLines) {

    public record FieldChange(String field, Object before, Object after) {
    }
}
