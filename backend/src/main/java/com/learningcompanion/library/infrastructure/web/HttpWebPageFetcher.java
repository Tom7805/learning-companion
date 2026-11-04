package com.learningcompanion.library.infrastructure.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Lấy trang web thật. Chống SSRF theo từng bước: trước mỗi yêu cầu (kể cả đích của mỗi lần chuyển hướng, không để thư
 * viện tự theo) đều kiểm đường dẫn và mọi địa chỉ IP mà tên miền phân giải ra. Tổng thời gian lấy một trang (kể cả đọc
 * nội dung) có giới hạn; nội dung vượt cỡ tối đa thì dừng đọc ngay chứ không tải hết rồi mới bỏ.
 */
@Component
@ConditionalOnProperty(name = "app.library.web-fetch.mode", havingValue = "http", matchIfMissing = true)
public class HttpWebPageFetcher implements WebPageFetcher {

    private static final String USER_AGENT = "LearningCompanion/1.0 (library web import)";

    private final WebAddressPolicy policy;
    private final WebFetchProperties properties;
    private final HttpClient client;

    public HttpWebPageFetcher(WebAddressPolicy policy, WebFetchProperties properties) {
        this.policy = policy;
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    @Override
    public FetchedPage fetch(URI start) {
        long deadline = System.nanoTime() + properties.timeout().toNanos();
        URI uri = start;
        for (int hop = 0; hop <= properties.maxRedirects(); hop++) {
            policy.check(uri);
            resolveAndCheck(uri.getHost());
            HttpResponse<byte[]> response = send(uri, deadline);
            int status = response.statusCode();
            if (status >= 300 && status < 400 && status != 304) {
                Optional<String> location = response.headers().firstValue("Location");
                if (location.isEmpty()) {
                    throw new WebFetchException(WebFetchException.Reason.UNREACHABLE);
                }
                try {
                    uri = uri.resolve(location.get().strip()).normalize();
                } catch (IllegalArgumentException ex) {
                    throw new WebFetchException(WebFetchException.Reason.UNREACHABLE, ex);
                }
                continue;
            }
            if (status == 401 || status == 403 || status == 407) {
                throw new WebFetchException(WebFetchException.Reason.LOGIN_REQUIRED);
            }
            if (status == 404 || status == 410) {
                throw new WebFetchException(WebFetchException.Reason.NOT_FOUND);
            }
            if (status < 200 || status >= 300) {
                throw new WebFetchException(WebFetchException.Reason.UNREACHABLE);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("text/html");
            return new FetchedPage(uri, mediaType(contentType), charset(contentType), response.body());
        }
        throw new WebFetchException(WebFetchException.Reason.UNREACHABLE);
    }

    private void resolveAndCheck(String host) {
        String bare = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(bare);
        } catch (UnknownHostException ex) {
            throw new WebFetchException(WebFetchException.Reason.UNREACHABLE, ex);
        }
        // Một tên miền trỏ vừa ra ngoài vừa vào trong vẫn bị chặn: không biết lần kết nối sẽ chọn địa chỉ nào.
        for (InetAddress address : addresses) {
            policy.checkAddress(address);
        }
    }

    private HttpResponse<byte[]> send(URI uri, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new WebFetchException(WebFetchException.Reason.TIMEOUT);
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofNanos(remaining))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9")
                .GET()
                .build();
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, this::limitedBody);
        try {
            return future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new WebFetchException(WebFetchException.Reason.TIMEOUT, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WebFetchException(WebFetchException.Reason.UNREACHABLE, ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof WebFetchException fetch) {
                throw fetch;
            }
            if (cause instanceof java.net.http.HttpTimeoutException) {
                throw new WebFetchException(WebFetchException.Reason.TIMEOUT, cause);
            }
            throw new WebFetchException(WebFetchException.Reason.UNREACHABLE, cause);
        }
    }

    /** Chỉ đọc nội dung của trang đích (2xx) có kiểu HTML hay văn bản, và không quá cỡ tối đa. */
    private HttpResponse.BodySubscriber<byte[]> limitedBody(HttpResponse.ResponseInfo info) {
        int status = info.statusCode();
        if (status < 200 || status >= 300) {
            return HttpResponse.BodySubscribers.replacing(new byte[0]);
        }
        HttpHeaders headers = info.headers();
        String type = mediaType(headers.firstValue("Content-Type").orElse("text/html"));
        if (!type.equals("text/html") && !type.equals("application/xhtml+xml") && !type.equals("text/plain")) {
            return failed(WebFetchException.Reason.UNSUPPORTED);
        }
        long declared = headers.firstValueAsLong("Content-Length").orElse(-1);
        if (declared > properties.maxBytes()) {
            return failed(WebFetchException.Reason.TOO_LARGE);
        }
        return new LimitedSubscriber(properties.maxBytes());
    }

    private static HttpResponse.BodySubscriber<byte[]> failed(WebFetchException.Reason reason) {
        LimitedSubscriber subscriber = new LimitedSubscriber(0);
        subscriber.result.completeExceptionally(new WebFetchException(reason));
        return subscriber;
    }

    static String mediaType(String contentType) {
        int semicolon = contentType.indexOf(';');
        return (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType).strip().toLowerCase(Locale.ROOT);
    }

    static String charset(String contentType) {
        for (String part : contentType.split(";")) {
            String trimmed = part.strip();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                return trimmed.substring("charset=".length()).replace("\"", "").strip();
            }
        }
        return null;
    }

    /** Gom nội dung và hủy luồng ngay khi vượt cỡ tối đa. */
    private static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maxBytes;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        LimitedSubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (result.isDone()) {
                subscription.cancel();
            } else {
                subscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (result.isDone()) {
                return;
            }
            for (ByteBuffer item : items) {
                if (out.size() + item.remaining() > maxBytes) {
                    subscription.cancel();
                    result.completeExceptionally(new WebFetchException(WebFetchException.Reason.TOO_LARGE));
                    return;
                }
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                out.write(chunk, 0, chunk.length);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable instanceof IOException io ? io : throwable);
        }

        @Override
        public void onComplete() {
            result.complete(out.toByteArray());
        }
    }
}
