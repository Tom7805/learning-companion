package com.learningcompanion.privacy.application;

import com.learningcompanion.privacy.domain.Consent;
import com.learningcompanion.privacy.domain.ConsentHistoryRepository;
import com.learningcompanion.privacy.domain.ConsentRepository;
import com.learningcompanion.shared.export.DataExportContributor;
import com.learningcompanion.shared.export.ExportFormat;
import com.learningcompanion.shared.export.ExportWriter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Phần "Quyền riêng tư" của gói xuất: đồng ý hiện tại theo từng mục đích và toàn bộ lịch sử đồng ý. */
@Component
public class PrivacyExportContributor implements DataExportContributor {

    private final ConsentRepository consents;
    private final ConsentHistoryRepository history;

    public PrivacyExportContributor(ConsentRepository consents, ConsentHistoryRepository history) {
        this.consents = consents;
        this.history = history;
    }

    @Override
    public String section() {
        return "privacy";
    }

    @Override
    public String title() {
        return "Quyền riêng tư và đồng ý";
    }

    @Override
    public List<String> describe() {
        return List.of(
                "consents.json: trạng thái đồng ý hiện tại theo từng mục đích, kèm phiên bản văn bản đã đồng ý.",
                "consent-history.csv: mọi lần đồng ý hoặc rút lại, từ cũ đến mới.");
    }

    @Override
    public List<ExportFormat> formats() {
        return List.of(ExportFormat.JSON, ExportFormat.CSV);
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(UUID ownerId, ExportWriter writer) {
        List<Map<String, Object>> current = consents.findByUserId(ownerId).stream()
                .sorted(Comparator.comparing(Consent::getPurpose))
                .map(PrivacyExportContributor::consent)
                .toList();
        writer.json("consents.json", current);
        List<List<?>> rows = history.findByUserIdOrderByRecordedAtAsc(ownerId).stream()
                .<List<?>>map(entry -> List.of(entry.getRecordedAt(), entry.getPurpose().name(),
                        entry.isGranted() ? "GRANTED" : "WITHDRAWN", entry.getDocumentVersion()))
                .toList();
        writer.csv("consent-history.csv", List.of("recorded_at", "purpose", "decision", "document_version"), rows);
    }

    private static Map<String, Object> consent(Consent consent) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("purpose", consent.getPurpose().name());
        data.put("required", consent.getPurpose().isRequired());
        data.put("granted", consent.isGranted());
        data.put("documentVersion", consent.getDocumentVersion());
        data.put("updatedAt", consent.getUpdatedAt());
        return data;
    }
}
