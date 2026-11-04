package com.learningcompanion.library.application.ingestion;

import com.learningcompanion.ai.config.application.AiConfigResolver;
import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.gateway.AiGateway;
import com.learningcompanion.ai.gateway.model.ChatRequest;
import com.learningcompanion.ai.gateway.model.EmbeddingRequest;
import com.learningcompanion.ai.gateway.model.EmbeddingResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Tạo biểu diễn ngữ nghĩa cho mọi đoạn qua lớp kết nối (QTN-04), theo lô để tài liệu năm mươi trang chỉ cần vài lời
 * gọi. Vector của các nhà cung cấp khác nhau không so được với nhau, nên cả tài liệu phải do một nhà cung cấp tạo: lớp
 * kết nối chuyển sang dự phòng giữa chừng thì tạo lại cả tài liệu một lần.
 */
@Service
public class EmbeddingIndexer {

    static final int BATCH = 64;
    /** Văn bản quá dài bị cắt bớt trước khi gửi; đoạn chia đã ngắn hơn nhiều nên hiếm khi xảy ra. */
    static final int MAX_CHARS = 8000;

    private final AiGateway gateway;
    private final AiConfigResolver configs;

    public EmbeddingIndexer(AiGateway gateway, AiConfigResolver configs) {
        this.gateway = gateway;
        this.configs = configs;
    }

    public record Embeddings(List<float[]> vectors, String providerCode) {
    }

    public Embeddings embed(List<ChunkingService.Draft> drafts) {
        List<String> texts = drafts.stream().map(ChunkingService.Draft::embeddingText)
                .map(text -> text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text)
                .toList();
        Embeddings first = embedAll(texts);
        if (first != null) {
            return first;
        }
        Embeddings second = embedAll(texts);
        if (second == null) {
            throw new IllegalStateException("Nhà cung cấp biểu diễn ngữ nghĩa đổi liên tục giữa các lô");
        }
        return second;
    }

    /** Vector của cả tài liệu, hoặc {@code null} nếu các lô do nhà cung cấp khác nhau tạo. */
    private Embeddings embedAll(List<String> texts) {
        ChatRequest.Tuning tuning = configs.tuning(AiFeature.DOCUMENT_EMBEDDING);
        List<float[]> vectors = new ArrayList<>(texts.size());
        String provider = null;
        for (int from = 0; from < texts.size(); from += BATCH) {
            List<String> batch = texts.subList(from, Math.min(texts.size(), from + BATCH));
            EmbeddingResult result = gateway.embed(AiFeature.DOCUMENT_EMBEDDING.name(), new EmbeddingRequest(batch),
                    tuning);
            if (result.vectors().size() != batch.size()) {
                throw new IllegalStateException("Số vector trả về không khớp số đoạn");
            }
            if (provider != null && !Objects.equals(provider, result.providerCode())) {
                return null;
            }
            provider = result.providerCode();
            vectors.addAll(result.vectors());
        }
        return new Embeddings(vectors, provider);
    }
}
