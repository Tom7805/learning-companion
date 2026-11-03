package com.learningcompanion.ai.provider;

import com.learningcompanion.ai.gateway.model.ChatChunk;
import com.learningcompanion.ai.gateway.model.ChatRequest;
import com.learningcompanion.ai.gateway.model.ChatResult;
import com.learningcompanion.ai.gateway.model.EmbeddingRequest;
import com.learningcompanion.ai.gateway.model.EmbeddingResult;
import com.learningcompanion.ai.gateway.model.OcrResult;
import com.learningcompanion.ai.gateway.model.VisionRequest;
import java.util.function.Consumer;

/**
 * Bộ chuyển đổi cho một kiểu nhà cung cấp. Chỉ {@code AiGateway} gọi tới đây; chức năng nghiệp vụ không bao giờ dùng
 * trực tiếp (QTN-04). Mọi phương thức chặn cho tới khi xong và phải dừng khi luồng bị ngắt (hết thời gian chờ).
 * Khả năng kiểu nhà cung cấp không hỗ trợ thì ném {@link ProviderCallException} mã UNSUPPORTED.
 */
public interface AiProviderAdapter {

    AdapterType type();

    ChatResult streamText(ProviderDescriptor provider, ChatRequest request, Consumer<ChatChunk> onChunk);

    EmbeddingResult embed(ProviderDescriptor provider, EmbeddingRequest request);

    ChatResult describeImage(ProviderDescriptor provider, VisionRequest request);

    OcrResult recognizeText(ProviderDescriptor provider, VisionRequest request);
}
