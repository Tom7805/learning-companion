package com.learningcompanion.library.infrastructure.extractor;

import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.DocumentType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * Tệp trình chiếu: các trang theo đúng thứ tự trình chiếu (danh sách trang trong tệp, không theo tên tệp con); tiêu đề
 * trang là đề mục; bảng giữ hàng và ô. Mỗi khối gắn số thứ tự trang trình chiếu.
 */
@Component
public class PptxTextExtractor implements TextExtractor {

    static final String P = "http://schemas.openxmlformats.org/presentationml/2006/main";
    static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final Pattern SLIDE_FILE = Pattern.compile("ppt/slides/slide(\\d+)\\.xml");

    @Override
    public Set<DocumentType> types() {
        return Set.of(DocumentType.PPTX);
    }

    @Override
    public Extraction extract(byte[] file, String mimeType, PageRecognizer recognizer) {
        ZipPackage zip = ZipPackage.read(file);
        List<String> slides = slideOrder(zip);
        List<TextBlock> blocks = new ArrayList<>();
        try {
            for (int i = 0; i < slides.size(); i++) {
                byte[] slide = zip.get(slides.get(i));
                if (slide != null) {
                    parseSlide(ZipPackage.xml(slide), i + 1, blocks);
                }
            }
        } catch (XMLStreamException ex) {
            throw new UnreadableDocumentException("Không đọc được trang trình chiếu", ex);
        }
        return new Extraction(blocks, slides.size());
    }

    /** Thứ tự trang theo danh sách trong presentation.xml; tệp thiếu danh sách thì theo số trong tên tệp. */
    static List<String> slideOrder(ZipPackage zip) {
        byte[] presentation = zip.get("ppt/presentation.xml");
        byte[] rels = zip.get("ppt/_rels/presentation.xml.rels");
        List<String> ordered = new ArrayList<>();
        if (presentation != null && rels != null) {
            try {
                Map<String, String> targets = new LinkedHashMap<>();
                XMLStreamReader xml = ZipPackage.xml(rels);
                while (xml.hasNext()) {
                    if (xml.next() == XMLStreamConstants.START_ELEMENT && "Relationship".equals(xml.getLocalName())) {
                        targets.put(xml.getAttributeValue(null, "Id"),
                                ZipPackage.resolve("ppt/presentation.xml", xml.getAttributeValue(null, "Target")));
                    }
                }
                xml = ZipPackage.xml(presentation);
                while (xml.hasNext()) {
                    if (xml.next() == XMLStreamConstants.START_ELEMENT && "sldId".equals(xml.getLocalName())) {
                        String target = targets.get(xml.getAttributeValue(R, "id"));
                        if (target != null) {
                            ordered.add(target);
                        }
                    }
                }
            } catch (XMLStreamException ex) {
                ordered.clear();
            }
        }
        if (ordered.isEmpty()) {
            zip.entries().keySet().stream()
                    .map(SLIDE_FILE::matcher)
                    .filter(Matcher::matches)
                    .sorted(Comparator.comparingInt(match -> Integer.parseInt(match.group(1))))
                    .forEach(match -> ordered.add(match.group()));
        }
        if (ordered.isEmpty()) {
            throw new UnreadableDocumentException("Tệp trình chiếu không có trang nào");
        }
        return ordered;
    }

    private static void parseSlide(XMLStreamReader xml, int number, List<TextBlock> blocks)
            throws XMLStreamException {
        ChunkLocation location = ChunkLocation.of(ChunkLocation.Kind.SLIDE, number);
        boolean title = false;
        int tableDepth = 0;
        List<String> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();
        List<String> titleParts = new ArrayList<>();
        int titleIndex = -1;
        int slideStart = blocks.size();
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String ns = xml.getNamespaceURI();
                String name = xml.getLocalName();
                if (P.equals(ns) && "sp".equals(name)) {
                    title = false;
                } else if (P.equals(ns) && "ph".equals(name)) {
                    String type = xml.getAttributeValue(null, "type");
                    title = "title".equals(type) || "ctrTitle".equals(type);
                } else if (A.equals(ns)) {
                    switch (name) {
                        case "tbl" -> {
                            if (tableDepth == 0) {
                                rows.clear();
                            }
                            tableDepth++;
                        }
                        case "tr" -> cells.clear();
                        case "tc" -> cell.setLength(0);
                        case "p" -> {
                            if (tableDepth == 0) {
                                paragraph.setLength(0);
                            } else if (!cell.isEmpty()) {
                                cell.append(' ');
                            }
                        }
                        case "t" -> (tableDepth > 0 ? cell : paragraph).append(xml.getElementText());
                        case "br" -> (tableDepth > 0 ? cell : paragraph).append(tableDepth > 0 ? ' ' : '\n');
                        default -> {
                        }
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && A.equals(xml.getNamespaceURI())) {
                switch (xml.getLocalName()) {
                    case "tc" -> cells.add(Paragraphs.squash(cell.toString()));
                    case "tr" -> {
                        if (cells.stream().anyMatch(value -> !value.isEmpty())) {
                            rows.add(String.join(" | ", cells));
                        }
                    }
                    case "tbl" -> {
                        tableDepth--;
                        if (tableDepth == 0 && !rows.isEmpty()) {
                            blocks.add(TextBlock.text(location, TextBlock.Role.TABLE, String.join("\n", rows)));
                        }
                    }
                    case "p" -> {
                        String text = paragraph.toString().strip();
                        if (tableDepth == 0 && !text.isEmpty()) {
                            if (title) {
                                // Tiêu đề nhiều dòng gộp thành một đề mục.
                                titleParts.add(text);
                                if (titleIndex < 0) {
                                    // Tiêu đề luôn đứng đầu trang dù khung tiêu đề nằm sau trong tệp.
                                    titleIndex = slideStart;
                                    blocks.add(slideStart, TextBlock.text(location, TextBlock.Role.HEADING, text));
                                } else {
                                    blocks.set(titleIndex, TextBlock.text(location, TextBlock.Role.HEADING,
                                            String.join(" ", titleParts)));
                                }
                            } else {
                                blocks.add(TextBlock.text(location, TextBlock.Role.BODY, text));
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }
}
