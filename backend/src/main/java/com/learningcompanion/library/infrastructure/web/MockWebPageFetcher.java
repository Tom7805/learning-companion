package com.learningcompanion.library.infrastructure.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bản mô phỏng của lớp lấy trang web (QTN-04) cho máy phát triển và kiểm thử: trả các trang mẫu cố định theo đường dẫn,
 * không gọi ra mạng. Trang mẫu có đủ thanh điều hướng, quảng cáo và chân trang để kiểm phần tách nội dung chính, và có
 * trang chỉ có ô đăng nhập, trang không tồn tại, trang quá chậm, tệp không phải HTML.
 */
@Component
@ConditionalOnProperty(name = "app.library.web-fetch.mode", havingValue = "mock")
public class MockWebPageFetcher implements WebPageFetcher {

    public static final String SORTING_ARTICLE = "https://bai-viet.example/thuat-toan-sap-xep";
    public static final String LOGIN_WALL = "https://thanh-vien.example/bai-viet-rieng";
    public static final String MISSING = "https://bai-viet.example/khong-ton-tai";
    public static final String SLOW = "https://cham.example/trang";
    public static final String NOT_HTML = "https://bai-viet.example/tai-lieu.zip";
    /** Chuyển hướng tới một địa chỉ nội bộ: phải bị chặn như khi dán thẳng địa chỉ đó. */
    public static final String REDIRECT_TO_INTERNAL = "https://chuyen-huong.example/vao-trong";

    private static final String SORTING_HTML = """
            <!doctype html>
            <html lang="vi"><head><meta charset="utf-8">
            <title>Thuật toán sắp xếp: nổi bọt, chèn và nhanh | Góc Lập Trình</title>
            <meta property="og:title" content="Thuật toán sắp xếp: nổi bọt, chèn và nhanh">
            <script>window.tracker = 1;</script></head>
            <body>
            <header class="site-header"><a href="/">Góc Lập Trình</a></header>
            <nav class="menu"><a href="/">Trang chủ</a><a href="/c">Chuyên mục</a></nav>
            <div class="ad-banner">Quảng cáo: Khóa học lập trình giảm giá 50%</div>
            <main>
            <article>
            <h1>Thuật toán sắp xếp: nổi bọt, chèn và nhanh</h1>
            <p>Sắp xếp là bài toán đưa các phần tử của một dãy về đúng thứ tự tăng dần hoặc giảm dần.</p>
            <h2>Sắp xếp nổi bọt</h2>
            <p>Sắp xếp nổi bọt so sánh từng cặp phần tử kề nhau và đổi chỗ khi chúng sai thứ tự; độ phức tạp là O(n²).</p>
            <div class="sponsored-box">Tài trợ: Máy tính xách tay giá tốt</div>
            <h2>Sắp xếp chèn</h2>
            <p>Sắp xếp chèn lấy từng phần tử và chèn vào đúng vị trí trong phần đầu dãy đã được sắp xếp.</p>
            <h2>Sắp xếp nhanh</h2>
            <p>Sắp xếp nhanh chọn một phần tử chốt, chia dãy thành hai phần nhỏ hơn và lớn hơn chốt rồi sắp xếp đệ quy;
            trung bình chạy trong O(n log n).</p>
            <aside class="related">Bài liên quan: Cấu trúc dữ liệu cây</aside>
            </article>
            </main>
            <footer>© Góc Lập Trình. Bản quyền thuộc về tác giả.</footer>
            </body></html>
            """;

    private static final String LOGIN_HTML = """
            <!doctype html><html><head><meta charset="utf-8"><title>Đăng nhập để đọc tiếp</title></head>
            <body><main><h1>Đăng nhập</h1>
            <form action="/login" method="post"><input name="email"><input type="password" name="password">
            <button>Đăng nhập</button></form></main></body></html>
            """;

    private final WebAddressPolicy policy;

    public MockWebPageFetcher(WebAddressPolicy policy) {
        this.policy = policy;
    }

    @Override
    public FetchedPage fetch(URI uri) {
        policy.check(uri);
        String key = uri.toString().toLowerCase(Locale.ROOT);
        if (key.startsWith(REDIRECT_TO_INTERNAL)) {
            policy.check(URI.create("http://127.0.0.1/admin"));
        }
        Map<String, String> pages = Map.of(SORTING_ARTICLE, SORTING_HTML, LOGIN_WALL, LOGIN_HTML);
        for (Map.Entry<String, String> page : pages.entrySet()) {
            if (key.startsWith(page.getKey())) {
                return new FetchedPage(uri, "text/html", "utf-8", page.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
        if (key.startsWith(SLOW)) {
            throw new WebFetchException(WebFetchException.Reason.TIMEOUT);
        }
        if (key.startsWith(NOT_HTML)) {
            throw new WebFetchException(WebFetchException.Reason.UNSUPPORTED);
        }
        if (key.startsWith(MISSING) || uri.getHost().endsWith(".example")) {
            throw new WebFetchException(WebFetchException.Reason.NOT_FOUND);
        }
        throw new WebFetchException(WebFetchException.Reason.UNREACHABLE);
    }
}
