package com.learningcompanion.library.infrastructure.extractor;

import com.learningcompanion.library.domain.DocumentType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * Sách điện tử: các chương theo đúng thứ tự đọc (spine trong tệp mô tả sách), đoạn văn được đánh số liên tục qua các
 * chương.
 */
@Component
public class EpubTextExtractor implements TextExtractor {

    @Override
    public Set<DocumentType> types() {
        return Set.of(DocumentType.EPUB);
    }

    @Override
    public Extraction extract(byte[] file, String mimeType, PageRecognizer recognizer) {
        ZipPackage zip = ZipPackage.read(file);
        List<TextBlock> blocks = new ArrayList<>();
        for (String chapter : readingOrder(zip)) {
            byte[] content = zip.get(chapter);
            if (content != null) {
                HtmlBlocks.read(new String(content, StandardCharsets.UTF_8), blocks);
            }
        }
        return new Extraction(blocks, blocks.size());
    }

    static List<String> readingOrder(ZipPackage zip) {
        String opf = null;
        try {
            XMLStreamReader container = ZipPackage.xml(zip.require("META-INF/container.xml"));
            while (container.hasNext() && opf == null) {
                if (container.next() == XMLStreamConstants.START_ELEMENT
                        && "rootfile".equals(container.getLocalName())) {
                    opf = container.getAttributeValue(null, "full-path");
                }
            }
            if (opf == null) {
                throw new UnreadableDocumentException("Sách điện tử thiếu tệp mô tả");
            }
            Map<String, String> manifest = new HashMap<>();
            List<String> spine = new ArrayList<>();
            XMLStreamReader package_ = ZipPackage.xml(zip.require(opf));
            while (package_.hasNext()) {
                if (package_.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                switch (package_.getLocalName()) {
                    case "item" -> manifest.put(package_.getAttributeValue(null, "id"),
                            package_.getAttributeValue(null, "href"));
                    case "itemref" -> spine.add(package_.getAttributeValue(null, "idref"));
                    default -> {
                    }
                }
            }
            List<String> order = new ArrayList<>();
            for (String id : spine) {
                String href = manifest.get(id);
                if (href != null) {
                    order.add(ZipPackage.resolve(opf, href));
                }
            }
            return order;
        } catch (XMLStreamException ex) {
            throw new UnreadableDocumentException("Không đọc được mô tả sách điện tử", ex);
        }
    }
}
