package com.learningcompanion.ai.provider.anthropic;

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
import com.learningcompanion.ai.provider.HttpProviderSupport;
import com.learningcompanion.ai.provider.ProviderCallException;
import com.learningcompanion.ai.provider.ProviderDescriptor;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Giao diện Messages của Anthropic, trả theo luồng sự kiện. */
@Component
public class AnthropicAdapter implements AiProviderAdapter {

    static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    static final String API_VERSION = "2023-06-01";
    static final String OCR_INSTRUCTION = "Chép lại nguyên văn mọi chữ trong hình, giữ xuống dòng. "
            + "Không giải thích, không thêm chữ nào khác.";

    private final HttpProviderSupport http;
    private final JsonMapper json;

    public AnthropicAdapter(HttpProviderSupport http, JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    @Override
    public AdapterType type() {
        return AdapterType.ANTHROPIC;
    }

    @Override
    public ChatResult streamText(ProviderDescriptor provider, ChatRequest request, Consumer<ChatChunk> onChunk) {
        ArrayNode messages = json.createArrayNode();
        for (ChatRequest.Message message : request.messages()) {
            messages.addObject().put("role", message.role() == ChatRequest.Role.USER ? "user" : "assistant")
                    .put("content", message.text());
        }
        return messages(provider, provider.textModel(), request.system(), messages, request.maxOutputUnits(),
                onChunk);
    }

    @Override
    public EmbeddingResult embed(ProviderDescriptor provider, EmbeddingRequest request) {
        throw new ProviderCallException("UNSUPPORTED", "Anthropic không cung cấp biểu diễn ngữ nghĩa");
    }

    @Override
    public ChatResult describeImage(ProviderDescriptor provider, VisionRequest request) {
        return messages(provider, provider.visionModel(), null,
                imageMessages(request, request.question() == null ? "Mô tả hình ảnh này." : request.question()),
                1024, chunk -> {
                });
    }

    @Override
    public OcrResult recognizeText(ProviderDescriptor provider, VisionRequest request) {
        ChatResult result = messages(provider, provider.visionModel(), null, imageMessages(request, OCR_INSTRUCTION),
                4096, chunk -> {
                });
        return new OcrResult(result.text(), result.usage(), provider.code());
    }

    private ArrayNode imageMessages(VisionRequest request, String question) {
        ArrayNode messages = json.createArrayNode();
        ArrayNode content = messages.addObject().put("role", "user").putArray("content");
        content.addObject().put("type", "image").putObject("source").put("type", "base64")
                .put("media_type", request.mimeType())
                .put("data", Base64.getEncoder().encodeToString(request.image()));
        content.addObject().put("type", "text").put("text", question);
        return messages;
    }

    private ChatResult messages(ProviderDescriptor provider, String model, String system, ArrayNode messages,
                                int maxTokens, Consumer<ChatChunk> onChunk) {
        ObjectNode body = json.createObjectNode().put("model", model).put("stream", true)
                .put("max_tokens", maxTokens);
        if (system != null && !system.isBlank()) {
            body.put("system", system);
        }
        body.set("messages", messages);
        StringBuilder text = new StringBuilder();
        AtomicInteger input = new AtomicInteger();
        AtomicInteger output = new AtomicInteger();
        String base = provider.baseUrl() == null || provider.baseUrl().isBlank() ? DEFAULT_BASE_URL
                : HttpProviderSupport.trimSlash(provider.baseUrl());
        http.postJsonStream(base + "/v1/messages",
                Map.of("x-api-key", provider.secret(), "anthropic-version", API_VERSION),
                json.writeValueAsString(body), data -> {
                    JsonNode event = json.readTree(data);
                    switch (event.path("type").asString("")) {
                        case "message_start" -> input.set(event.path("message").path("usage")
                                .path("input_tokens").asInt());
                        case "content_block_delta" -> {
                            String delta = event.path("delta").path("text").asString(null);
                            if (delta != null && !delta.isEmpty()) {
                                text.append(delta);
                                onChunk.accept(new ChatChunk(delta));
                            }
                        }
                        case "message_delta" -> output.set(event.path("usage").path("output_tokens").asInt());
                        case "error" -> throw new ProviderCallException("PROVIDER_ERROR",
                                "Nhà cung cấp báo lỗi giữa luồng");
                        default -> {
                        }
                    }
                });
        return new ChatResult(text.toString(), new AiUsage(input.get(), output.get()), provider.code());
    }
}
