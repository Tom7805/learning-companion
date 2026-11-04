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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
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
    // Số lời gọi nhận dạng chữ sắp tới sẽ lỗi (âm: lỗi mãi cho tới khi đặt lại), để thử lỗi ở một trang (NCL-05-CN-003).
    private final AtomicInteger ocrFailures = new AtomicInteger();

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
        ocrFailures.set(0);
        scriptedReply = null;
        lastRequest = null;
    }

    /**
     * Cho {@code calls} lời gọi nhận dạng chữ tiếp theo lỗi như dịch vụ bên ngoài gặp sự cố; số âm thì lỗi cho tới khi
     * đặt lại. Chỉ ảnh hưởng nhận dạng chữ, các khả năng khác vẫn chạy.
     */
    public void failNextOcr(int calls) {
        ocrFailures.set(calls);
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
        if (ocrFailures.getAndUpdate(left -> left > 0 ? left - 1 : left) != 0) {
            throw new ProviderCallException("MOCK_OCR_FAILURE", "Bản mô phỏng đang giả lập lỗi nhận dạng chữ");
        }
        String text = recognizedText(request.image());
        return new OcrResult(text, new AiUsage(imageUnits(request), words(text)), provider.code());
    }

    /**
     * Chữ "nhận dạng" được: ảnh PNG mang sẵn chữ trong khối {@value #OCR_TEXT_KEY} (ảnh mẫu của môi trường thử) thì trả
     * đúng chữ đó; ảnh một màu (trang trắng) thì không có chữ; còn lại là câu mô phỏng cố định.
     */
    static String recognizedText(byte[] image) {
        String embedded = embeddedText(image);
        if (embedded != null) {
            return embedded;
        }
        return isPlain(image) ? "" : "Văn bản mô phỏng nhận dạng từ hình ảnh.";
    }

    /** Tên khối chữ quốc tế (iTXt) trong ảnh PNG mẫu mà bản mô phỏng đọc như chữ nhận dạng được. */
    public static final String OCR_TEXT_KEY = "MockOcrText";

    private static String embeddedText(byte[] png) {
        if (png.length < 8 || (png[0] & 0xff) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(png);
        buffer.position(8);
        while (buffer.remaining() >= 12) {
            int length = buffer.getInt();
            byte[] type = new byte[4];
            buffer.get(type);
            if (length < 0 || length > buffer.remaining() - 4) {
                return null;
            }
            byte[] data = new byte[length];
            buffer.get(data);
            buffer.getInt();
            if ("iTXt".equals(new String(type, StandardCharsets.US_ASCII))) {
                String text = iTxt(data);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String iTxt(byte[] data) {
        int keyEnd = indexOf(data, 0);
        if (keyEnd < 0 || !OCR_TEXT_KEY.equals(new String(data, 0, keyEnd, StandardCharsets.ISO_8859_1))
                || keyEnd + 2 >= data.length || data[keyEnd + 1] != 0) {
            return null;
        }
        int languageEnd = indexOf(data, keyEnd + 3);
        int translatedEnd = languageEnd < 0 ? -1 : indexOf(data, languageEnd + 1);
        if (translatedEnd < 0) {
            return null;
        }
        return new String(data, translatedEnd + 1, data.length - translatedEnd - 1, StandardCharsets.UTF_8);
    }

    private static int indexOf(byte[] data, int from) {
        for (int i = from; i < data.length; i++) {
            if (data[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Ảnh gần như một màu, ví dụ trang giấy trắng chụp lại. Ảnh không đọc được thì coi như có nội dung. */
    private static boolean isPlain(byte[] image) {
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image));
            if (decoded == null) {
                return false;
            }
            int min = 255;
            int max = 0;
            int stepX = Math.max(1, decoded.getWidth() / 200);
            int stepY = Math.max(1, decoded.getHeight() / 200);
            for (int y = 0; y < decoded.getHeight(); y += stepY) {
                for (int x = 0; x < decoded.getWidth(); x += stepX) {
                    int rgb = decoded.getRGB(x, y);
                    int luminance = (((rgb >> 16) & 0xff) * 299 + ((rgb >> 8) & 0xff) * 587 + (rgb & 0xff) * 114) / 1000;
                    min = Math.min(min, luminance);
                    max = Math.max(max, luminance);
                }
            }
            return max - min < 24;
        } catch (IOException ex) {
            return false;
        }
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
