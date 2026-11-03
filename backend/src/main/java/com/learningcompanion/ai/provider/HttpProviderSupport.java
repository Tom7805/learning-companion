package com.learningcompanion.ai.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Gửi yêu cầu HTTP tới nhà cung cấp. Thời gian chờ tổng do lớp kết nối quản lý bằng cách ngắt luồng; ở đây chỉ đặt
 * giới hạn kết nối. Lỗi trả về chỉ mang mã trạng thái, không chép thân phản hồi (có thể chứa nội dung lời gọi).
 */
@Component
public class HttpProviderSupport {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** Gửi JSON và đọc toàn bộ phản hồi. */
    public String postJson(String url, Map<String, String> headers, String body) {
        HttpResponse<String> response = send(url, headers, body, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response.statusCode());
        return response.body();
    }

    /** Gửi JSON và đọc phản hồi dạng luồng sự kiện, mỗi dòng "data:" một lần gọi {@code onData}. */
    public void postJsonStream(String url, Map<String, String> headers, String body, Consumer<String> onData) {
        HttpResponse<InputStream> response = send(url, headers, body, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream stream = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            ensureSuccess(response.statusCode());
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new ProviderCallException("INTERRUPTED", "Lời gọi bị dừng");
                }
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (!data.isEmpty() && !"[DONE]".equals(data)) {
                        onData.accept(data);
                    }
                }
            }
        } catch (IOException ex) {
            throw new ProviderCallException("NETWORK", "Mất kết nối tới nhà cung cấp", ex);
        }
    }

    private <T> HttpResponse<T> send(String url, Map<String, String> headers, String body,
                                     HttpResponse.BodyHandler<T> handler) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(request::header);
        try {
            return client.send(request.build(), handler);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException("INTERRUPTED", "Lời gọi bị dừng", ex);
        } catch (IOException ex) {
            throw new ProviderCallException("NETWORK", "Không kết nối được tới nhà cung cấp", ex);
        }
    }

    private static void ensureSuccess(int status) {
        if (status == 401 || status == 403) {
            throw new ProviderCallException("AUTH", "Nhà cung cấp từ chối khóa bí mật (HTTP " + status + ")");
        }
        if (status == 429) {
            throw new ProviderCallException("RATE_LIMITED", "Nhà cung cấp giới hạn tốc độ (HTTP 429)");
        }
        if (status >= 400) {
            throw new ProviderCallException("HTTP_" + status, "Nhà cung cấp trả lỗi HTTP " + status);
        }
    }

    public static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
