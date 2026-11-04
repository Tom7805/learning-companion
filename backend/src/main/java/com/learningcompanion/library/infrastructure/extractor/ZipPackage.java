package com.learningcompanion.library.infrastructure.extractor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Tệp nén văn phòng (Word, PowerPoint, sách điện tử) đọc trong bộ nhớ, có giới hạn tổng dung lượng bung ra. Trình đọc
 * XML tắt DTD và thực thể ngoài để tệp độc hại không đọc được tệp trên máy chủ.
 */
final class ZipPackage {

    /** Tải lên đã chặn tệp bung quá lớn; giới hạn ở đây là chốt thứ hai. */
    private static final long MAX_TOTAL = 512L * 1024 * 1024;

    private static final XMLInputFactory XML = XMLInputFactory.newFactory();

    static {
        XML.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        XML.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XML.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        XML.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    private final Map<String, byte[]> entries;

    private ZipPackage(Map<String, byte[]> entries) {
        this.entries = entries;
    }

    static ZipPackage read(byte[] file) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file))) {
            ZipEntry entry;
            byte[] buffer = new byte[16 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_TOTAL) {
                        throw new UnreadableDocumentException("Tệp nén bung ra quá lớn");
                    }
                    out.write(buffer, 0, read);
                }
                entries.put(entry.getName(), out.toByteArray());
            }
        } catch (IOException ex) {
            throw new UnreadableDocumentException("Không đọc được tệp nén", ex);
        }
        if (entries.isEmpty()) {
            throw new UnreadableDocumentException("Tệp nén rỗng");
        }
        return new ZipPackage(entries);
    }

    byte[] get(String name) {
        return entries.get(name);
    }

    byte[] require(String name) {
        byte[] entry = entries.get(name);
        if (entry == null) {
            throw new UnreadableDocumentException("Thiếu phần " + name);
        }
        return entry;
    }

    Map<String, byte[]> entries() {
        return entries;
    }

    static XMLStreamReader xml(byte[] content) {
        try {
            return XML.createXMLStreamReader(new ByteArrayInputStream(content));
        } catch (XMLStreamException ex) {
            throw new UnreadableDocumentException("Không đọc được XML", ex);
        }
    }

    /** Đường dẫn {@code href} tính từ thư mục chứa tệp {@code base}, bỏ "./" và "../". */
    static String resolve(String base, String href) {
        String decoded = java.net.URLDecoder.decode(href.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
        int hash = decoded.indexOf('#');
        if (hash >= 0) {
            decoded = decoded.substring(0, hash);
        }
        if (decoded.startsWith("/")) {
            return decoded.substring(1);
        }
        int slash = base.lastIndexOf('/');
        java.util.Deque<String> parts = new java.util.ArrayDeque<>();
        if (slash > 0) {
            for (String part : base.substring(0, slash).split("/")) {
                parts.addLast(part);
            }
        }
        for (String part : decoded.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                parts.pollLast();
            } else {
                parts.addLast(part);
            }
        }
        return String.join("/", parts);
    }
}
