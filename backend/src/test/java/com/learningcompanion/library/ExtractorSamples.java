package com.learningcompanion.library;

import com.learningcompanion.shared.storage.TestFiles;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tệp Word, trình chiếu và sách điện tử nhỏ có đề mục, bảng và công thức, cho kiểm thử trích văn bản. */
public final class ExtractorSamples {

    private ExtractorSamples() {
    }

    public static byte[] docx() {
        String body = """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math">
                  <w:body>
                    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chương 1. Giới hạn</w:t></w:r></w:p>
                    <w:p><w:r><w:t xml:space="preserve">Giới hạn mô tả giá trị mà hàm số </w:t></w:r><w:r><w:t>tiến tới.</w:t></w:r></w:p>
                    <w:p/>
                    <w:tbl>
                      <w:tr><w:tc><w:p><w:r><w:t>Hàm số</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Giới hạn</w:t></w:r></w:p></w:tc></w:tr>
                      <w:tr><w:tc><w:p><w:r><w:t>sin x / x</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>1</w:t></w:r></w:p></w:tc></w:tr>
                    </w:tbl>
                    <w:p><m:oMathPara><m:oMath><m:r><m:t>lim</m:t></m:r><m:r><m:t>(1+1/n)^n = e</m:t></m:r></m:oMath></m:oMathPara></w:p>
                    <w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>1.2 Liên tục</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Hàm liên tục khi giới hạn bằng giá trị.</w:t></w:r></w:p>
                  </w:body>
                </w:document>""";
        return TestFiles.zip(new LinkedHashMap<>(Map.of(
                "[Content_Types].xml", xml("<Types/>"),
                "word/document.xml", xml(body))));
    }

    /**
     * Ba trang trình chiếu; tệp con đặt tên ngược với thứ tự trình chiếu (slide3 trình chiếu đầu tiên) để kiểm thứ tự
     * lấy theo danh sách trang.
     */
    public static byte[] pptx() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", xml("<Types/>"));
        entries.put("ppt/presentation.xml", xml("""
                <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                   xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <p:sldIdLst><p:sldId id="256" r:id="rId3"/><p:sldId id="257" r:id="rId2"/><p:sldId id="258" r:id="rId1"/></p:sldIdLst>
                </p:presentation>"""));
        entries.put("ppt/_rels/presentation.xml.rels", xml("""
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Target="slides/slide1.xml"/>
                  <Relationship Id="rId2" Target="slides/slide2.xml"/>
                  <Relationship Id="rId3" Target="slides/slide3.xml"/>
                </Relationships>"""));
        entries.put("ppt/slides/slide3.xml", slide("Mở đầu", "<a:p><a:r><a:t>Mục tiêu buổi học</a:t></a:r></a:p>"));
        entries.put("ppt/slides/slide2.xml", slide("Bảng số liệu", """
                <a:tbl><a:tr><a:tc><a:txBody><a:p><a:r><a:t>Năm</a:t></a:r></a:p></a:txBody></a:tc>
                <a:tc><a:txBody><a:p><a:r><a:t>Dân số</a:t></a:r></a:p></a:txBody></a:tc></a:tr>
                <a:tr><a:tc><a:txBody><a:p><a:r><a:t>2020</a:t></a:r></a:p></a:txBody></a:tc>
                <a:tc><a:txBody><a:p><a:r><a:t>97 triệu</a:t></a:r></a:p></a:txBody></a:tc></a:tr></a:tbl>"""));
        entries.put("ppt/slides/slide1.xml", slide("Tổng kết", "<a:p><a:r><a:t>Ôn lại ba ý chính</a:t></a:r></a:p>"));
        return TestFiles.zip(entries);
    }

    public static byte[] epub() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
        entries.put("META-INF/container.xml", xml("""
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>"""));
        entries.put("OEBPS/content.opf", xml("""
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <manifest>
                    <item id="c1" href="text/chuong-1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/chuong%202.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c2"/><itemref idref="c1"/></spine>
                </package>"""));
        entries.put("OEBPS/text/chuong 2.xhtml", xml("""
                <!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Hai</title></head>
                <body><h1>Lời nói đầu</h1><p>Cuốn sách dành cho học sinh&nbsp;lớp 12 &amp; người tự học.</p></body></html>"""));
        entries.put("OEBPS/text/chuong-1.xhtml", xml("""
                <html><body><h2>Chương một</h2><p>Đoạn thứ nhất.</p><p>Đoạn thứ hai.</p>
                <table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table></body></html>"""));
        return TestFiles.zip(entries);
    }

    private static byte[] slide(String title, String body) {
        return xml("""
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                       xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                  <p:cSld><p:spTree>
                    <p:sp><p:txBody>%s</p:txBody></p:sp>
                    <p:sp><p:nvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                      <p:txBody><a:p><a:r><a:t>%s</a:t></a:r></a:p></p:txBody></p:sp>
                  </p:spTree></p:cSld>
                </p:sld>""".formatted(body, title));
    }

    private static byte[] xml(String body) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + body).getBytes(StandardCharsets.UTF_8);
    }
}
