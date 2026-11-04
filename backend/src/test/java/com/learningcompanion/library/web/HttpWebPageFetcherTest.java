package com.learningcompanion.library.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learningcompanion.library.infrastructure.web.BlockedAddressException;
import com.learningcompanion.library.infrastructure.web.HttpWebPageFetcher;
import com.learningcompanion.library.infrastructure.web.WebAddressPolicy;
import com.learningcompanion.library.infrastructure.web.WebFetchException;
import com.learningcompanion.library.infrastructure.web.WebFetchProperties;
import com.learningcompanion.library.infrastructure.web.WebPageFetcher;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bộ lấy trang thật, kiểm với một máy chủ HTTP chạy trên máy kiểm thử. Máy chủ này nằm ở địa chỉ nội bộ nên các ca lấy
 * trang dùng chính sách cho phép mạng nội bộ; ca chặn dùng chính sách thật.
 */
class HttpWebPageFetcherTest {

    private static final WebFetchProperties PROPERTIES = new WebFetchProperties("http", Duration.ofSeconds(2),
            Duration.ofSeconds(2), 64 * 1024, 3);

    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/bai", exchange -> reply(exchange, 200, "text/html; charset=utf-8",
                "<h1>Bài viết</h1>"));
        server.createContext("/chuyen", exchange -> {
            exchange.getResponseHeaders().add("Location", "/bai");
            reply(exchange, 302, "text/html", "");
        });
        server.createContext("/vong", exchange -> {
            exchange.getResponseHeaders().add("Location", "/vong");
            reply(exchange, 301, "text/html", "");
        });
        server.createContext("/vao-trong", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/");
            reply(exchange, 302, "text/html", "");
        });
        server.createContext("/rieng", exchange -> reply(exchange, 401, "text/html", "Cần đăng nhập"));
        server.createContext("/cam", exchange -> reply(exchange, 403, "text/html", "Cấm"));
        server.createContext("/mat", exchange -> reply(exchange, 404, "text/html", "Không có"));
        server.createContext("/loi", exchange -> reply(exchange, 500, "text/html", "Lỗi"));
        server.createContext("/zip", exchange -> reply(exchange, 200, "application/zip", "PK"));
        server.createContext("/lon", exchange -> reply(exchange, 200, "text/html", "a".repeat(200 * 1024)));
        server.createContext("/cham", exchange -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            reply(exchange, 200, "text/html", "muộn");
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    @DisplayName("Lấy trang và đi theo chuyển hướng, giữ đường dẫn cuối cùng và bảng mã")
    void fetchesAndFollowsRedirects() {
        WebPageFetcher.FetchedPage page = local().fetch(URI.create(base + "/chuyen"));
        assertThat(page.finalUri().getPath()).isEqualTo("/bai");
        assertThat(page.contentType()).isEqualTo("text/html");
        assertThat(page.charset()).isEqualTo("utf-8");
        assertThat(new String(page.body(), StandardCharsets.UTF_8)).contains("Bài viết");
    }

    @Test
    @DisplayName("TC-03: chính sách thật chặn máy chủ chạy ở địa chỉ nội bộ")
    void realPolicyBlocksLoopback() {
        HttpWebPageFetcher fetcher = new HttpWebPageFetcher(new WebAddressPolicy(), PROPERTIES);
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/bai")))
                .isInstanceOf(BlockedAddressException.class);
    }

    @Test
    @DisplayName("Chuyển hướng vào địa chỉ nội bộ bị chặn ở bước kiểm đích chuyển hướng")
    void blocksRedirectIntoInternalNetwork() {
        // Máy chủ ban đầu được phép (chỉ trong kiểm thử), nhưng đích 169.254.169.254 vẫn phải bị chặn.
        WebAddressPolicy onlyLoopback = new WebAddressPolicy() {
            @Override
            public void checkAddress(InetAddress address) {
                if (!address.isLoopbackAddress()) {
                    super.checkAddress(address);
                }
            }

            @Override
            public void check(URI uri) {
                if (!"127.0.0.1".equals(uri.getHost())) {
                    super.check(uri);
                }
            }
        };
        HttpWebPageFetcher fetcher = new HttpWebPageFetcher(onlyLoopback, PROPERTIES);
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/vao-trong")))
                .isInstanceOf(BlockedAddressException.class);
    }

    @Test
    @DisplayName("TC-04: 401, 403 là cần đăng nhập; 404 là không tìm thấy; 500 là không kết nối được")
    void mapsStatusCodes() {
        assertReason("/rieng", WebFetchException.Reason.LOGIN_REQUIRED);
        assertReason("/cam", WebFetchException.Reason.LOGIN_REQUIRED);
        assertReason("/mat", WebFetchException.Reason.NOT_FOUND);
        assertReason("/loi", WebFetchException.Reason.UNREACHABLE);
    }

    @Test
    @DisplayName("Giới hạn kiểu nội dung, kích thước, thời gian chờ và số lần chuyển hướng")
    void enforcesLimits() {
        assertReason("/zip", WebFetchException.Reason.UNSUPPORTED);
        assertReason("/lon", WebFetchException.Reason.TOO_LARGE);
        assertReason("/cham", WebFetchException.Reason.TIMEOUT);
        assertReason("/vong", WebFetchException.Reason.UNREACHABLE);
    }

    private void assertReason(String path, WebFetchException.Reason reason) {
        assertThatThrownBy(() -> local().fetch(URI.create(base + path)))
                .isInstanceOf(WebFetchException.class)
                .extracting(ex -> ((WebFetchException) ex).reason())
                .isEqualTo(reason);
    }

    private HttpWebPageFetcher local() {
        return new HttpWebPageFetcher(new WebAddressPolicy(true), PROPERTIES);
    }

    private static void reply(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
