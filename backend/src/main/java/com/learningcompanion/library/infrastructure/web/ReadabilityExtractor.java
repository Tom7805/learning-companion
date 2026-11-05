package com.learningcompanion.library.infrastructure.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/**
 * Tách nội dung chính của một trang web (NCL-05-CN-004 TC-01): bỏ script, biểu mẫu, thanh điều hướng, đầu và chân
 * trang, cột bên, khối quảng cáo, tài trợ, chia sẻ hay bài liên quan; lấy phần {@code article}/{@code main} có nhiều chữ
 * nhất, rồi làm sạch thành HTML tĩnh chỉ còn đề mục, đoạn văn, danh sách, bảng và mã. Bản chụp này là thứ được lưu và
 * chia đoạn; nội dung trang luôn chỉ là dữ liệu (QTN-16) nên mọi thẻ chạy được đều bị bỏ.
 */
@Component
public class ReadabilityExtractor {

    /** Trang có ô mật khẩu mà ít chữ hơn ngưỡng này được coi là trang đăng nhập (TC-04). */
    static final int LOGIN_WALL_MAX_TEXT = 600;

    private static final String NOISE_TAGS = "script, style, noscript, template, iframe, object, embed, canvas, svg, "
            + "form, button, input, select, textarea, nav, aside, dialog, img, picture, video, audio, "
            + "[role=navigation], [role=banner], [role=contentinfo], [role=complementary], [role=search], "
            + "[aria-hidden=true], [hidden]";
    private static final Pattern NOISE_NAMES = Pattern.compile(
            "(^|[\\s_-])(ads?|advert\\w*|adsense|banner|sponsor\\w*|promo\\w*|cookies?|consent|newsletter|share|"
                    + "sharing|social|related|comments?|popup|modal|subscribe|subscription|breadcrumbs?|sidebar|"
                    + "menu|widget|outbrain|taboola)($|[\\s_-])", Pattern.CASE_INSENSITIVE);
    private static final Safelist KEEP = Safelist.relaxed()
            .removeTags("img", "a", "span", "font", "u", "small", "big", "sub", "sup", "colgroup", "col")
            .addTags("section", "article", "figure", "figcaption", "main", "mark", "math", "mi", "mo", "mn", "msup",
                    "mrow", "mfrac", "msub", "msqrt");

    public Article extract(byte[] body, String contentType, String charset, URI base) {
        if ("text/plain".equals(contentType)) {
            String text = new String(body, charsetOr(charset)).strip();
            return plainArticle(text, base);
        }
        Document page;
        try {
            page = Jsoup.parse(new ByteArrayInputStream(body), charsetName(charset), base.toString());
        } catch (IOException ex) {
            throw new WebFetchException(WebFetchException.Reason.UNSUPPORTED, ex);
        }
        String title = title(page);
        boolean hasPassword = !page.select("input[type=password]").isEmpty();
        Element body0 = page.body();
        int pageText = body0 == null ? 0 : body0.text().length();
        if (hasPassword && pageText < LOGIN_WALL_MAX_TEXT) {
            throw new WebFetchException(WebFetchException.Reason.LOGIN_REQUIRED);
        }
        if (body0 == null) {
            throw new WebFetchException(WebFetchException.Reason.NO_CONTENT);
        }
        page.select(NOISE_TAGS).remove();
        // Đầu và chân trang của cả trang là phần khung; đầu trang trong bài viết (chứa tiêu đề) thì giữ.
        for (Element element : page.select("header, footer")) {
            if (element.closest("article") == null) {
                element.remove();
            }
        }
        for (Element element : new Elements(body0.getAllElements())) {
            if (element != body0 && element.parent() != null && isNoise(element)) {
                element.remove();
            }
        }
        Element root = mainContent(body0);
        String text = root.text().strip();
        if (text.isEmpty()) {
            throw new WebFetchException(WebFetchException.Reason.NO_CONTENT);
        }
        String cleaned = Jsoup.clean(root.html(), base.toString(), KEEP,
                new Document.OutputSettings().charset(StandardCharsets.UTF_8).prettyPrint(false)
                        .escapeMode(Entities.EscapeMode.base));
        if (title.isEmpty()) {
            Element heading = root.selectFirst("h1, h2");
            title = heading == null ? hostOf(base) : heading.text().strip();
        }
        boolean startsWithTitle = root.selectFirst("h1") != null;
        String snapshot = "<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>" + Entities.escape(title)
                + "</title></head><body><article>" + (startsWithTitle ? "" : "<h1>" + Entities.escape(title) + "</h1>")
                + cleaned + "</article></body></html>\n";
        return new Article(title, snapshot, text.length());
    }

    private static Article plainArticle(String text, URI base) {
        if (text.isEmpty()) {
            throw new WebFetchException(WebFetchException.Reason.NO_CONTENT);
        }
        String title = hostOf(base);
        StringBuilder html = new StringBuilder("<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>")
                .append(Entities.escape(title)).append("</title></head><body><article>");
        for (String paragraph : text.split("\\R\\s*\\R")) {
            if (!paragraph.isBlank()) {
                html.append("<p>").append(Entities.escape(paragraph.strip())).append("</p>");
            }
        }
        return new Article(title, html.append("</article></body></html>\n").toString(), text.length());
    }

    private static boolean isNoise(Element element) {
        String names = element.className() + " " + element.id();
        return !names.isBlank() && NOISE_NAMES.matcher(names).find()
                && element.selectFirst("h1") == null;
    }

    /** {@code article} hoặc {@code main} có nhiều chữ nhất; không có thì cả thân trang. */
    private static Element mainContent(Element body) {
        Element best = null;
        for (Element candidate : body.select("article, main, [role=main]")) {
            if (best == null || candidate.text().length() > best.text().length()) {
                best = candidate;
            }
        }
        return best != null && !best.text().isBlank() ? best : body;
    }

    private static String title(Document page) {
        Element og = page.selectFirst("meta[property=og:title]");
        if (og != null && !og.attr("content").isBlank()) {
            return og.attr("content").strip();
        }
        String title = page.title().strip();
        if (!title.isEmpty()) {
            return title;
        }
        Element h1 = page.selectFirst("h1");
        return h1 == null ? "" : h1.text().strip();
    }

    private static String hostOf(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static String charsetName(String charset) {
        return charset == null ? null : charsetOr(charset).name();
    }

    private static Charset charsetOr(String charset) {
        try {
            return charset == null ? StandardCharsets.UTF_8 : Charset.forName(charset);
        } catch (IllegalArgumentException ex) {
            return StandardCharsets.UTF_8;
        }
    }

    /** Nội dung chính đã tách: tiêu đề, bản chụp HTML tĩnh để lưu và số ký tự chữ. */
    public record Article(String title, String html, int textLength) {
    }
}
