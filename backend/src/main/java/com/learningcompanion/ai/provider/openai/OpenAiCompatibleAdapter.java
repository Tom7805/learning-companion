package com.learningcompanion.ai.provider.openai;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Nhà cung cấp theo giao diện kiểu OpenAI: {@code /chat/completions} theo luồng và {@code /embeddings}. */
@Component
public class OpenAiCompatibleAdapter implements AiProviderAdapter {

    static final String OCR_INSTRUCTION = "Chép lại nguyên văn mọi chữ trong hình, giữ xuống dòng. "
            + "Không giải thích, không thêm chữ nào khác.";

    private final HttpProviderSupport http;
    private final JsonMapper json;

    public OpenAiCompatibleAdapter(HttpProviderSupport http, JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    @Override
    public AdapterType type() {
        return AdapterType.OPENAI_COMPATIBLE;
    }

    @Override
    public ChatResult streamText(ProviderDescriptor provider, ChatRequest request, Consumer<ChatChunk> onChunk) {
        ArrayNode messages = json.createArrayNode();
        if (request.system() != null && !request.system().isBlank()) {
            messages.addObject().put("role", "system").put("content", request.system());
        }
        for (ChatRequest.Message message : request.messages()) {
            messages.addObject().put("role", message.role() == ChatRequest.Role.USER ? "user" : "assistant")
                    .put("content", message.text());
        }
        return chat(provider, provider.textModel(), messages, request.maxOutputUnits(), request.tuning().temperature(),
                onChunk);
    }

    @Override
    public EmbeddingResult embed(ProviderDescriptor provider, EmbeddingRequest request) {
        ObjectNode body = json.createObjectNode().put("model", provider.embeddingModel());
        ArrayNode input = body.putArray("input");
        request.texts().forEach(input::add);
        JsonNode response = json.readTree(http.postJson(url(provider, "/embeddings"), headers(provider),
                json.writeValueAsString(body)));
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : response.path("data")) {
            JsonNode values = item.path("embedding");
            float[] vector = new float[values.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = values.get(i).floatValue();
            }
            vectors.add(vector);
        }
        if (vectors.size() != request.texts().size()) {
            throw new ProviderCallException("BAD_RESPONSE", "Số vector không khớp số đoạn văn bản");
        }
        return new EmbeddingResult(vectors, new AiUsage(response.path("usage").path("prompt_tokens").asInt(), 0),
                provider.code());
    }

    @Override
    public ChatResult describeImage(ProviderDescriptor provider, VisionRequest request) {
        return chat(provider, provider.visionModel(), imageMessages(request, request.question()), 1024, null,
                chunk -> {
                });
    }

    @Override
    public OcrResult recognizeText(ProviderDescriptor provider, VisionRequest request) {
        ChatResult result = chat(provider, provider.visionModel(), imageMessages(request, OCR_INSTRUCTION), 4096,
                null, chunk -> {
                });
        return new OcrResult(result.text(), result.usage(), provider.code());
    }

    private ArrayNode imageMessages(VisionRequest request, String question) {
        ArrayNode messages = json.createArrayNode();
        ArrayNode content = messages.addObject().put("role", "user").putArray("content");
        content.addObject().put("type", "text").put("text", question == null ? "Mô tả hình ảnh này." : question);
        content.addObject().put("type", "image_url").putObject("image_url").put("url",
                "data:" + request.mimeType() + ";base64," + Base64.getEncoder().encodeToString(request.image()));
        return messages;
    }

    private ChatResult chat(ProviderDescriptor provider, String model, ArrayNode messages, int maxTokens,
                            Double temperature, Consumer<ChatChunk> onChunk) {
        ObjectNode body = json.createObjectNode().put("model", model).put("stream", true)
                .put("max_tokens", maxTokens);
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        body.set("messages", messages);
        body.putObject("stream_options").put("include_usage", true);
        StringBuilder text = new StringBuilder();
        AtomicReference<AiUsage> usage = new AtomicReference<>(AiUsage.NONE);
        http.postJsonStream(url(provider, "/chat/completions"), headers(provider), json.writeValueAsString(body),
                data -> {
                    JsonNode event = json.readTree(data);
                    for (JsonNode choice : event.path("choices")) {
                        String delta = choice.path("delta").path("content").asString(null);
                        if (delta != null && !delta.isEmpty()) {
                            text.append(delta);
                            onChunk.accept(new ChatChunk(delta));
                        }
                    }
                    JsonNode reported = event.path("usage");
                    if (reported.isObject()) {
                        usage.set(new AiUsage(reported.path("prompt_tokens").asInt(),
                                reported.path("completion_tokens").asInt()));
                    }
                });
        return new ChatResult(text.toString(), usage.get(), provider.code());
    }

    private static String url(ProviderDescriptor provider, String path) {
        if (provider.baseUrl() == null || provider.baseUrl().isBlank()) {
            throw new ProviderCallException("MISCONFIGURED", "Thiếu địa chỉ dịch vụ");
        }
        return HttpProviderSupport.trimSlash(provider.baseUrl()) + path;
    }

    private static Map<String, String> headers(ProviderDescriptor provider) {
        return Map.of("Authorization", "Bearer " + provider.secret());
    }
}
