package com.learningcompanion.library.infrastructure.extractor;

import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.DocumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * Tài liệu Word: mỗi đoạn văn khác rỗng được đánh số theo thứ tự trong tài liệu; đoạn có kiểu đề mục (Heading, Title)
 * là đề mục; bảng giữ cấu trúc hàng và ô, cả bảng tính là một đoạn; công thức (Office Math) giữ ký hiệu.
 */
@Component
public class DocxTextExtractor implements TextExtractor {

    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";

    @Override
    public Set<DocumentType> types() {
        return Set.of(DocumentType.DOCX);
    }

    @Override
    public Extraction extract(byte[] file, String mimeType, PageRecognizer recognizer) {
        ZipPackage zip = ZipPackage.read(file);
        List<TextBlock> blocks = new ArrayList<>();
        XMLStreamReader xml = ZipPackage.xml(zip.require("word/document.xml"));
        try {
            parse(xml, blocks);
        } catch (XMLStreamException ex) {
            throw new UnreadableDocumentException("Không đọc được nội dung tài liệu Word", ex);
        }
        return new Extraction(blocks, blocks.size());
    }

    private static void parse(XMLStreamReader xml, List<TextBlock> blocks) throws XMLStreamException {
        int tableDepth = 0;
        List<String> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();
        String style = null;
        boolean math = false;
        boolean plainText = false;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String ns = xml.getNamespaceURI();
                String name = xml.getLocalName();
                if (W.equals(ns)) {
                    switch (name) {
                        case "tbl" -> {
                            if (tableDepth == 0) {
                                rows.clear();
                            }
                            tableDepth++;
                        }
                        case "tr" -> {
                            if (tableDepth == 1) {
                                cells.clear();
                            }
                        }
                        case "tc" -> {
                            if (tableDepth == 1) {
                                cell.setLength(0);
                            }
                        }
                        case "p" -> {
                            if (tableDepth == 0) {
                                paragraph.setLength(0);
                                style = null;
                                math = false;
                                plainText = false;
                            } else if (!cell.isEmpty()) {
                                cell.append(' ');
                            }
                        }
                        case "pStyle" -> style = xml.getAttributeValue(W, "val");
                        case "t" -> {
                            String text = xml.getElementText();
                            (tableDepth > 0 ? cell : paragraph).append(text);
                            plainText |= tableDepth == 0 && !text.isBlank();
                        }
                        case "tab" -> (tableDepth > 0 ? cell : paragraph).append('\t');
                        case "br", "cr" -> (tableDepth > 0 ? cell : paragraph).append(tableDepth > 0 ? ' ' : '\n');
                        default -> {
                        }
                    }
                } else if (M.equals(ns) && "t".equals(name)) {
                    (tableDepth > 0 ? cell : paragraph).append(xml.getElementText());
                    math = true;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && W.equals(xml.getNamespaceURI())) {
                switch (xml.getLocalName()) {
                    case "tc" -> {
                        if (tableDepth == 1) {
                            cells.add(Paragraphs.squash(cell.toString()));
                        }
                    }
                    case "tr" -> {
                        if (tableDepth == 1 && cells.stream().anyMatch(value -> !value.isEmpty())) {
                            rows.add(String.join(" | ", cells));
                        }
                    }
                    case "tbl" -> {
                        tableDepth--;
                        if (tableDepth == 0 && !rows.isEmpty()) {
                            blocks.add(block(blocks.size() + 1, TextBlock.Role.TABLE, String.join("\n", rows)));
                        }
                    }
                    case "p" -> {
                        if (tableDepth == 0) {
                            String text = paragraph.toString().strip();
                            if (!text.isEmpty()) {
                                TextBlock.Role role = isHeading(style) ? TextBlock.Role.HEADING
                                        : math && !plainText ? TextBlock.Role.FORMULA : TextBlock.Role.BODY;
                                blocks.add(block(blocks.size() + 1, role, text));
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private static TextBlock block(int number, TextBlock.Role role, String text) {
        return TextBlock.text(ChunkLocation.of(ChunkLocation.Kind.PARAGRAPH, number), role, text);
    }

    static boolean isHeading(String style) {
        if (style == null) {
            return false;
        }
        String lower = style.toLowerCase(Locale.ROOT).replace(" ", "");
        return lower.startsWith("heading") || lower.equals("title") || lower.equals("subtitle")
                || lower.startsWith("tieude") || lower.startsWith("u") && lower.length() == 2
                && Character.isDigit(lower.charAt(1));
    }
}
