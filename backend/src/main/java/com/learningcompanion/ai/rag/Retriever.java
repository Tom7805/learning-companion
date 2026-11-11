package com.learningcompanion.ai.rag;

import com.learningcompanion.ai.config.application.AiConfigResolver;
import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.gateway.AiGateway;
import com.learningcompanion.ai.gateway.model.EmbeddingRequest;
import com.learningcompanion.ai.gateway.model.EmbeddingResult;
import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentChunk;
import com.learningcompanion.library.domain.DocumentChunkRepository;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.library.domain.DocumentStatus;
import com.learningcompanion.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Truy xuất các đoạn liên quan nhất tới câu hỏi, chỉ trong các tài liệu đang chọn và đã sẵn sàng (QTN-22). Điểm của
 * mỗi đoạn kết hợp mức khớp từ ngữ (tiếng và cặp tiếng, có trọng số theo độ hiếm) với độ gần nghĩa của vector, nhưng vector
 * chỉ so được với tài liệu do cùng nhà cung cấp lập chỉ mục. Đoạn không khớp từ ngữ nào chỉ được nhận khi rất gần nghĩa,
 * để tài liệu không nói gì về câu hỏi thì trợ lý trả lời "không tìm thấy" thay vì bịa (TC-03).
 */
@Service
public class Retriever {

    private static final Logger log = LoggerFactory.getLogger(Retriever.class);

    /** Số đoạn tối đa ghép vào ngữ cảnh, và tối đa từ một tài liệu, để câu trả lời không lệch về một tài liệu dài. */
    static final int LIMIT = 6;
    static final int PER_DOCUMENT = 4;
    /** Phần trọng số từ ngữ của câu hỏi một đoạn phải khớp mới được coi là liên quan khi không khớp cặp tiếng nào. */
    static final double MIN_COVERAGE = 0.6;

    private final DocumentChunkRepository chunks;
    private final DocumentRepository documents;
    private final AiGateway gateway;
    private final AiConfigResolver configs;
    private final double minSimilarity;

    public Retriever(DocumentChunkRepository chunks, DocumentRepository documents, AiGateway gateway,
                     AiConfigResolver configs,
                     @Value("${app.assistant.retrieval.min-similarity:0.78}") double minSimilarity) {
        this.chunks = chunks;
        this.documents = documents;
        this.gateway = gateway;
        this.configs = configs;
        this.minSimilarity = minSimilarity;
    }

    /** Một đoạn được chọn: đoạn, tài liệu của nó, thứ tự đoạn tại vị trí đó (như khung xem dùng) và điểm. */
    public record Hit(DocumentChunk chunk, Document document, int passage, double score) {
    }

    /** Các đoạn liên quan nhất trong các tài liệu {@code documentIds} của người học, điểm cao trước. */
    @Transactional(readOnly = true)
    public List<Hit> retrieve(UUID ownerId, Collection<UUID> documentIds, String question) {
        if (documentIds.isEmpty()) {
            return List.of();
        }
        List<DocumentChunk> pool = chunks.findReadyByOwnerIdAndDocumentIdIn(ownerId, documentIds,
                DocumentStatus.READY);
        Map<UUID, Document> byId = documents.findAllByOwnerIdAndIdIn(ownerId, documentIds).stream()
                .collect(Collectors.toMap(Document::getId, document -> document));
        return rank(pool, byId, question, embedQuestion(question, byId.values()));
    }

    /**
     * Các tài liệu sẵn sàng khác của người học (không nằm trong {@code excluded}) có nói về câu hỏi, để gợi ý chọn thêm
     * khi tài liệu đang chọn không có thông tin (TC-04). Chỉ so từ ngữ, không gọi lớp kết nối.
     */
    @Transactional(readOnly = true)
    public List<Document> elsewhere(UUID ownerId, Collection<UUID> excluded, String question, int limit) {
        Set<UUID> skip = new HashSet<>(excluded);
        List<DocumentChunk> pool = chunks.findReadyByOwnerId(ownerId, DocumentStatus.READY).stream()
                .filter(chunk -> !skip.contains(chunk.getDocumentId())).toList();
        if (pool.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = pool.stream().map(DocumentChunk::getDocumentId).collect(Collectors.toSet());
        Map<UUID, Document> byId = documents.findAllByOwnerIdAndIdIn(ownerId, ids).stream()
                .collect(Collectors.toMap(Document::getId, document -> document));
        Map<UUID, Document> found = new LinkedHashMap<>();
        for (Hit hit : rank(pool, byId, question, null)) {
            found.putIfAbsent(hit.document().getId(), hit.document());
        }
        return found.values().stream().limit(limit).toList();
    }

    private List<Hit> rank(List<DocumentChunk> pool, Map<UUID, Document> byId, String question, QueryVector vector) {
        List<String> words = TextTerms.words(question);
        boolean folded = TextTerms.unaccented(words);
        Set<String> terms = new HashSet<>(words);
        Set<String> pairs = TextTerms.pairs(words);
        if (terms.isEmpty() && vector == null) {
            return List.of();
        }
        List<Indexed> indexed = pool.stream().filter(chunk -> byId.containsKey(chunk.getDocumentId()))
                .map(chunk -> Indexed.of(chunk, folded)).toList();
        Map<String, Double> weight = weights(terms, indexed);
        double total = weight.values().stream().mapToDouble(Double::doubleValue).sum();

        List<Hit> scored = new ArrayList<>();
        Map<UUID, List<DocumentChunk>> byDocument = pool.stream()
                .collect(Collectors.groupingBy(DocumentChunk::getDocumentId));
        for (Indexed item : indexed) {
            double matched = 0;
            for (String term : terms) {
                if (item.words().contains(term)) {
                    matched += weight.get(term);
                }
            }
            long pairHits = pairs.stream().filter(item.pairs()::contains).count();
            double coverage = total == 0 ? 0 : matched / total;
            double similarity = vector == null ? 0 : vector.similarity(item.chunk(), byId.get(item.chunk().getDocumentId()));
            boolean lexical = pairHits > 0 || coverage >= MIN_COVERAGE;
            boolean semantic = similarity >= minSimilarity;
            if (!lexical && !semantic) {
                continue;
            }
            double score = coverage + 0.5 * pairHits / Math.max(1, pairs.size()) + 0.5 * Math.max(0, similarity);
            Document document = byId.get(item.chunk().getDocumentId());
            scored.add(new Hit(item.chunk(), document, passageOf(item.chunk(), byDocument.get(document.getId())),
                    score));
        }
        scored.sort(Comparator.comparingDouble(Hit::score).reversed()
                .thenComparing(hit -> hit.chunk().getOrdinal()));
        Map<UUID, Integer> perDocument = new HashMap<>();
        List<Hit> chosen = new ArrayList<>();
        for (Hit hit : scored) {
            int count = perDocument.merge(hit.document().getId(), 1, Integer::sum);
            if (count <= PER_DOCUMENT) {
                chosen.add(hit);
            }
            if (chosen.size() == LIMIT) {
                break;
            }
        }
        return chosen;
    }

    /** Tiếng hiếm trong phạm vi tài liệu có trọng số cao hơn tiếng xuất hiện ở hầu hết các đoạn. */
    private static Map<String, Double> weights(Set<String> terms, List<Indexed> indexed) {
        Map<String, Double> weight = new HashMap<>();
        int n = Math.max(1, indexed.size());
        for (String term : terms) {
            long containing = indexed.stream().filter(item -> item.words().contains(term)).count();
            weight.put(term, Math.log(1 + (double) (n + 1) / (containing + 1)));
        }
        return weight;
    }

    /** Thứ tự đoạn tại vị trí của nó, như khung xem tài liệu đánh số (NCL-05-CN-005). */
    public static int passageOf(DocumentChunk chunk, List<DocumentChunk> sameDocument) {
        ChunkLocation at = chunk.getLocation();
        List<DocumentChunk> matching = sameDocument.stream()
                .filter(other -> other.getLocation().kind() == at.kind() && other.getLocation().number() <= at.number()
                        && other.getLocation().end() >= at.number())
                .sorted(Comparator.comparingInt(DocumentChunk::getOrdinal)).toList();
        int index = matching.indexOf(chunk);
        return index < 0 ? 1 : index + 1;
    }

    /** Vector của câu hỏi; lỗi hay không có tài liệu nào được lập chỉ mục thì chỉ so từ ngữ. */
    private QueryVector embedQuestion(String question, Collection<Document> scope) {
        if (scope.stream().map(Document::getIndexProvider).allMatch(Objects::isNull)) {
            return null;
        }
        try {
            EmbeddingResult result = gateway.embed(AiFeature.DOCUMENT_EMBEDDING.name(),
                    new EmbeddingRequest(List.of(question)), configs.tuning(AiFeature.DOCUMENT_EMBEDDING));
            return result.vectors().isEmpty() ? null
                    : new QueryVector(result.vectors().get(0), result.providerCode());
        } catch (BusinessException | IllegalStateException ex) {
            log.info("Không tạo được vector cho câu hỏi, chỉ so từ ngữ: {}", ex.getMessage());
            return null;
        }
    }

    private record QueryVector(float[] values, String providerCode) {

        double similarity(DocumentChunk chunk, Document document) {
            float[] other = chunk.getEmbedding();
            if (document == null || !Objects.equals(document.getIndexProvider(), providerCode) || other == null
                    || other.length != values.length) {
                return 0;
            }
            double dot = 0;
            double left = 0;
            double right = 0;
            for (int i = 0; i < values.length; i++) {
                dot += values[i] * other[i];
                left += values[i] * values[i];
                right += other[i] * other[i];
            }
            return left == 0 || right == 0 ? 0 : dot / Math.sqrt(left * right);
        }
    }

    private record Indexed(DocumentChunk chunk, Set<String> words, Set<String> pairs) {

        static Indexed of(DocumentChunk chunk, boolean folded) {
            String text = (chunk.getHeading() == null ? "" : chunk.getHeading() + "\n") + chunk.getContent();
            List<String> words = TextTerms.words(text);
            if (folded) {
                words = words.stream().map(TextTerms::fold).toList();
            }
            return new Indexed(chunk, new HashSet<>(words), TextTerms.pairs(words));
        }
    }
}
