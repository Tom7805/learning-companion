package com.learningcompanion.ai.provider.mock;

import com.learningcompanion.ai.gateway.model.AiUsage;
import com.learningcompanion.ai.gateway.model.ChatChunk;
import com.learningcompanion.ai.gateway.model.ChatRequest;
import com.learningcompanion.ai.gateway.model.ChatResult;
import com.learningcompanion.ai.gateway.model.EmbeddingRequest;
import com.learningcompanion.ai.gateway.model.EmbeddingResult;
import com.learningcompanion.ai.gateway.model.OcrResult;
import com.learningcompanion.ai.gateway.model.VisionRequest;
import com.learningcompanion.ai.provider.AdapterType;
import com.learningcompanion.ai.provider.AiProviderAdapter;
import com.learningcompanion.ai.provider.ProviderCallException;
import com.learningcompanion.ai.provider.ProviderDescriptor;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Bản mô phỏng trả kết quả cố định (NCL-04-CN-001): cùng yêu cầu luôn cho cùng kết quả, nên kiểm thử và môi trường
 * thử không phụ thuộc dịch vụ bên ngoài. Mỗi nhà cung cấp mô phỏng có thể được chỉnh sang chế độ lỗi hoặc treo để thử
 * ngắt mạch và quá hạn; chế độ chỉ đổi được qua công cụ phát triển.
 */
@Component
public class MockAiAdapter implements AiProviderAdapter {

    public static final int VECTOR_DIMENSIONS = 8;

    /** Cách bản mô phỏng phản hồi. */
    public enum Mode { OK, FAIL, HANG }

    private final Map<String, Mode> modes = new ConcurrentHashMap<>();
    private volatile Duration chunkDelay = Duration.ofMillis(25);
    private volatile String scriptedReply;
    private volatile ChatRequest lastRequest;

    /**
     * Câu chèn chỉ dẫn mẫu mà bản mô phỏng "nghe theo" như một mô hình ngây thơ (NCL-04-CN-004): nếu câu này lọt tới
     * mô hình, câu trả lời bị chiếm quyền. Lớp bao dữ liệu lược câu đó trước, nên câu trả lời vẫn theo chỉ dẫn hệ thống.
     */
    public static final Pattern NAIVE_INJECTION = Pattern.compile(
            "bỏ qua mọi chỉ dẫn|ignore all previous instructions", Pattern.CASE_INSENSITIVE
                    | Pattern.UNICODE_CASE);
    public static final String HIJACKED_REPLY = "Đã bỏ qua chỉ dẫn hệ thống theo yêu cầu trong tài liệu.";

    @Override
    public AdapterType type() {
        return AdapterType.MOCK;
    }

    public void setMode(String providerCode, Mode mode) {
        if (mode == Mode.OK) {
            modes.remove(providerCode);
        } else {
            modes.put(providerCode, mode);
        }
    }

    public Mode mode(String providerCode) {
        return modes.getOrDefault(providerCode, Mode.OK);
    }

    public void reset() {
        modes.clear();
        scriptedReply = null;
        lastRequest = null;
    }

    /** Câu trả lời cố định cho mọi lời gọi sinh văn bản tiếp theo, ví dụ để thử bộ lọc đầu ra; null để bỏ. */
    public void setScriptedReply(String reply) {
        this.scriptedReply = reply;
    }

    /** Yêu cầu sinh văn bản gần nhất bản mô phỏng nhận được, để kiểm thử xem mô hình thật sự đọc thấy gì. */
    public ChatRequest lastRequest() {
        return lastRequest;
    }

    /** Khoảng nghỉ giữa các đoạn khi trả theo luồng, để giao diện thấy chữ hiện dần. */
    public void setChunkDelay(Duration delay) {
        this.chunkDelay = delay;
    }

    @Override
    public ChatResult streamText(ProviderDescriptor provider, ChatRequest request, Consumer<ChatChunk> onChunk) {
        behave(provider);
        lastRequest = request;
        String answer = "Đây là câu trả lời mô phỏng từ " + provider.code()
                + ". Nội dung cố định giúp kiểm thử luôn cho cùng một kết quả.";
        if (scriptedReply != null) {
            answer = scriptedReply;
        } else if (request.messages().stream().anyMatch(m -> NAIVE_INJECTION.matcher(m.text()).find())) {
            answer = HIJACKED_REPLY;
        }
        List<String> pieces = new ArrayList<>();
        for (String word : answer.split(" ")) {
            pieces.add(pieces.isEmpty() ? word : " " + word);
        }
        for (String piece : pieces) {
            pause(chunkDelay);
            onChunk.accept(new ChatChunk(piece));
        }
        int input = words(request.system());
        for (ChatRequest.Message message : request.messages()) {
            input += words(message.text());
        }
        return new ChatResult(answer, new AiUsage(input, pieces.size()), provider.code());
    }

    @Override
    public EmbeddingResult embed(ProviderDescriptor provider, EmbeddingRequest request) {
        behave(provider);
        List<float[]> vectors = new ArrayList<>();
        int input = 0;
        for (String text : request.texts()) {
            vectors.add(vectorOf(text));
            input += words(text);
        }
        return new EmbeddingResult(vectors, new AiUsage(input, 0), provider.code());
    }

    @Override
    public ChatResult describeImage(ProviderDescriptor provider, VisionRequest request) {
        behave(provider);
        String answer = "Hình ảnh mô phỏng có " + request.image().length + " byte.";
        return new ChatResult(answer, new AiUsage(imageUnits(request) + words(request.question()), words(answer)),
                provider.code());
    }

    @Override
    public OcrResult recognizeText(ProviderDescriptor provider, VisionRequest request) {
        behave(provider);
        String text = "Văn bản mô phỏng nhận dạng từ hình ảnh.";
        return new OcrResult(text, new AiUsage(imageUnits(request), words(text)), provider.code());
    }

    /** Vector cố định theo nội dung: cùng đoạn văn bản luôn cho cùng vector, độ dài 1. */
    static float[] vectorOf(String text) {
        float[] vector = new float[VECTOR_DIMENSIONS];
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            vector[i % VECTOR_DIMENSIONS] += (bytes[i] & 0xff) / 255f;
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < vector.length && norm > 0; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    private void behave(ProviderDescriptor provider) {
        switch (mode(provider.code())) {
            case FAIL -> throw new ProviderCallException("MOCK_FAILURE", "Bản mô phỏng đang ở chế độ lỗi");
            case HANG -> pause(Duration.ofHours(1));
            case OK -> {
            }
        }
    }

    private static void pause(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException("INTERRUPTED", "Lời gọi bị dừng", ex);
        }
    }

    private static int imageUnits(VisionRequest request) {
        return Math.max(1, request.image().length / 1024);
    }

    private static int words(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
