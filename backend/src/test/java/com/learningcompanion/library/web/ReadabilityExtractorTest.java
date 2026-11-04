package com.learningcompanion.library.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learningcompanion.library.infrastructure.web.ReadabilityExtractor;
import com.learningcompanion.library.infrastructure.web.WebFetchException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NCL-05-CN-004 — tách nội dung chính của trang web, bỏ điều hướng và quảng cáo. */
class ReadabilityExtractorTest {

    private static final URI BASE = URI.create("https://www.goc-lap-trinh.vn/bai/sap-xep");
    private final ReadabilityExtractor extractor = new ReadabilityExtractor();

    @Test
    @DisplayName("TC-01: giữ tiêu đề và nội dung bài, bỏ điều hướng, quảng cáo, chân trang, script")
    void keepsArticleAndDropsChrome() {
        String html = """
                <html><head><title>Sắp xếp nhanh | Góc Lập Trình</title><script>evil()</script></head><body>
                <header>Logo</header><nav><a href="/">Trang chủ</a></nav>
                <div id="ads-top">Quảng cáo khóa học</div>
                <article><header><h1>Sắp xếp nhanh</h1></header>
                <p onclick="evil()">Chọn phần tử chốt rồi chia dãy.</p>
                <div class="share-buttons">Chia sẻ Facebook</div>
                <ul><li>Bước một</li><li>Bước hai</li></ul>
                <pre><code>quickSort(a, lo, hi)</code></pre>
                <iframe src="https://quang-cao.example"></iframe>
                </article>
                <aside>Bài liên quan</aside><footer>Bản quyền</footer></body></html>
                """;
        ReadabilityExtractor.Article article = extract(html);

        assertThat(article.title()).isEqualTo("Sắp xếp nhanh | Góc Lập Trình");
        assertThat(article.html()).contains("<h1>Sắp xếp nhanh</h1>", "Chọn phần tử chốt rồi chia dãy.",
                "<li>Bước hai</li>", "quickSort(a, lo, hi)");
        assertThat(article.html()).doesNotContain("Quảng cáo", "Trang chủ", "Chia sẻ", "Bài liên quan", "Bản quyền",
                "Logo", "evil", "iframe", "onclick", "<script");
    }

    @Test
    @DisplayName("Tiêu đề lấy từ og:title khi có; trang không có h1 thì thêm tiêu đề vào đầu bản chụp")
    void prefersOpenGraphTitle() {
        ReadabilityExtractor.Article article = extract("""
                <html><head><meta property="og:title" content="Cây nhị phân"><title>X</title></head>
                <body><main><p>Mỗi nút có tối đa hai con.</p></main></body></html>""");
        assertThat(article.title()).isEqualTo("Cây nhị phân");
        assertThat(article.html()).contains("<title>Cây nhị phân</title>", "<h1>Cây nhị phân</h1>");
    }

    @Test
    @DisplayName("Chọn khối article nhiều chữ nhất")
    void picksLongestArticle() {
        ReadabilityExtractor.Article article = extract("""
                <body><article><p>Ngắn.</p></article>
                <article><h1>Bài chính</h1><p>Đây là đoạn văn dài hơn hẳn của bài viết chính.</p></article></body>""");
        assertThat(article.html()).contains("Bài chính").doesNotContain("Ngắn.");
    }

    @Test
    @DisplayName("TC-04: trang chỉ có ô đăng nhập được báo cần đăng nhập")
    void detectsLoginWall() {
        assertThatThrownBy(() -> extract("""
                <body><h1>Đăng nhập</h1><form><input name="u"><input type="password"></form></body>"""))
                .isInstanceOf(WebFetchException.class)
                .extracting(ex -> ((WebFetchException) ex).reason())
                .isEqualTo(WebFetchException.Reason.LOGIN_REQUIRED);
    }

    @Test
    @DisplayName("Trang không có chữ nào được báo không có nội dung")
    void rejectsEmptyPage() {
        assertThatThrownBy(() -> extract("<html><body><nav>Menu</nav><script>x()</script></body></html>"))
                .isInstanceOf(WebFetchException.class)
                .extracting(ex -> ((WebFetchException) ex).reason())
                .isEqualTo(WebFetchException.Reason.NO_CONTENT);
    }

    @Test
    @DisplayName("Đọc đúng bảng mã khai báo của trang")
    void respectsDeclaredCharset() {
        byte[] body = "<body><p>Café crème brûlée</p></body>".getBytes(Charset.forName("windows-1252"));
        ReadabilityExtractor.Article article = extractor.extract(body, "text/html", "windows-1252", BASE);
        assertThat(article.html()).contains("Café crème brûlée");
    }

    @Test
    @DisplayName("Trang văn bản thuần thành các đoạn văn, tiêu đề là tên miền")
    void wrapsPlainText() {
        ReadabilityExtractor.Article article = extractor.extract(
                "Đoạn một.\n\nĐoạn <hai>.".getBytes(StandardCharsets.UTF_8), "text/plain", null, BASE);
        assertThat(article.title()).isEqualTo("goc-lap-trinh.vn");
        assertThat(article.html()).contains("<p>Đoạn một.</p>", "<p>Đoạn &lt;hai&gt;.</p>");
    }

    private ReadabilityExtractor.Article extract(String html) {
        return extractor.extract(html.getBytes(StandardCharsets.UTF_8), "text/html", "utf-8", BASE);
    }
}
