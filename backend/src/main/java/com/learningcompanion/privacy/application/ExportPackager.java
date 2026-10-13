package com.learningcompanion.privacy.application;

import com.learningcompanion.privacy.domain.DataExportRequest;
import com.learningcompanion.shared.export.DataExportContributor;
import com.learningcompanion.shared.export.DataExportRegistry;
import com.learningcompanion.shared.export.ExportFormat;
import com.learningcompanion.shared.export.ExportWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dựng gói xuất dạng zip: mỗi phần dữ liệu một thư mục do contributor của chức năng đó ghi, kèm tệp hướng dẫn
 * đọc (README.txt) và bảng kê máy đọc được (manifest.json) ở thư mục gốc. Mọi văn bản là UTF-8.
 */
@Component
public class ExportPackager {

    public static final String FORMAT = "learning-companion-export";
    public static final int FORMAT_VERSION = 1;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter READABLE = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final DataExportRegistry registry;
    private final JsonMapper json;
    private final String readmeTemplate;

    public ExportPackager(DataExportRegistry registry, JsonMapper json) {
        this.registry = registry;
        this.json = json;
        this.readmeTemplate = load("templates/export/README.txt");
    }

    /** Tên tệp tải về và cũng là tên thư mục gốc trong gói. */
    public static String packageName(Instant generatedAt, ZoneId zone) {
        return "learning-companion-export-" + DAY.withZone(zone).format(generatedAt);
    }

    public Built build(UUID ownerId, String ownerEmail, Instant generatedAt, ZoneId zone) {
        String root = packageName(generatedAt, zone) + "/";
        List<SectionOutput> outputs = new ArrayList<>();
        for (DataExportContributor contributor : registry.contributors()) {
            CollectingWriter writer = new CollectingWriter(json);
            contributor.contribute(ownerId, writer);
            outputs.add(new SectionOutput(contributor, writer.entries()));
        }

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(root + "README.txt", readme(ownerEmail, generatedAt, zone, outputs).getBytes(StandardCharsets.UTF_8));
        files.put(root + "manifest.json", json.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(manifest(ownerId, generatedAt, outputs)));
        for (SectionOutput output : outputs) {
            output.entries().forEach((path, content) ->
                    files.put(root + output.contributor().section() + "/" + path, content));
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            FileTime time = FileTime.from(generatedAt);
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setLastModifiedTime(time);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Không dựng được gói xuất", ex);
        }
        List<DataExportRequest.Section> sections = outputs.stream()
                .map(output -> new DataExportRequest.Section(output.contributor().section(),
                        output.contributor().title(), output.entries().size()))
                .toList();
        return new Built(bytes.toByteArray(), files.size(), sections);
    }

    private String readme(String ownerEmail, Instant generatedAt, ZoneId zone, List<SectionOutput> outputs) {
        StringBuilder sections = new StringBuilder();
        for (SectionOutput output : outputs) {
            DataExportContributor contributor = output.contributor();
            sections.append(contributor.title()).append("  (thư mục ").append(contributor.section()).append("/)\n");
            sections.append("Định dạng: ").append(contributor.formats().stream().map(ExportFormat::label)
                    .collect(Collectors.joining(", "))).append('\n');
            for (String line : contributor.describe()) {
                sections.append("  - ").append(line).append('\n');
            }
            if (output.entries().isEmpty()) {
                sections.append("  (Chưa có dữ liệu ở phần này.)\n");
            }
            sections.append('\n');
        }
        return readmeTemplate
                .replace("{{generatedAt}}", READABLE.withZone(zone).format(generatedAt) + " (" + zone.getId() + ")")
                .replace("{{owner}}", ownerEmail)
                .replace("{{sections}}", sections.toString().stripTrailing())
                .replace("\n", "\r\n");
    }

    private static Map<String, Object> manifest(UUID ownerId, Instant generatedAt, List<SectionOutput> outputs) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", FORMAT);
        manifest.put("formatVersion", FORMAT_VERSION);
        manifest.put("generatedAt", generatedAt.toString());
        manifest.put("accountId", ownerId.toString());
        manifest.put("sections", outputs.stream().map(output -> {
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("id", output.contributor().section());
            section.put("title", output.contributor().title());
            section.put("formats", output.contributor().formats().stream().map(Enum::name).toList());
            section.put("files", output.entries().keySet().stream()
                    .map(path -> output.contributor().section() + "/" + path).toList());
            return section;
        }).toList());
        return manifest;
    }

    private static String load(String location) {
        try (InputStream in = new ClassPathResource(location).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException ex) {
            throw new UncheckedIOException("Thiếu mẫu tệp hướng dẫn đọc của gói xuất", ex);
        }
    }

    public record Built(byte[] content, int fileCount, List<DataExportRequest.Section> sections) {
    }

    private record SectionOutput(DataExportContributor contributor, Map<String, byte[]> entries) {
    }

    /** Gom tệp của một phần vào bộ nhớ, làm sạch đường dẫn và đánh số khi trùng tên. */
    static final class CollectingWriter implements ExportWriter {

        private final JsonMapper json;
        private final Map<String, byte[]> entries = new LinkedHashMap<>();
        private final Set<String> taken = new HashSet<>();

        CollectingWriter(JsonMapper json) {
            this.json = json;
        }

        Map<String, byte[]> entries() {
            return entries;
        }

        @Override
        public String json(String path, Object value) {
            return bytes(path, json.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        }

        @Override
        public String csv(String path, List<String> headers, List<List<?>> rows) {
            StringBuilder csv = new StringBuilder();
            appendRow(csv, headers);
            for (List<?> row : rows) {
                appendRow(csv, row);
            }
            byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
            byte[] content = new byte[UTF8_BOM.length + body.length];
            System.arraycopy(UTF8_BOM, 0, content, 0, UTF8_BOM.length);
            System.arraycopy(body, 0, content, UTF8_BOM.length, body.length);
            return bytes(path, content);
        }

        @Override
        public String text(String path, String content) {
            return bytes(path, content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String bytes(String path, byte[] content) {
            String actual = unique(ExportWriter.path(path.split("/", -1)));
            entries.put(actual, content);
            return actual;
        }

        private String unique(String path) {
            int slash = path.lastIndexOf('/');
            String folder = path.substring(0, slash + 1);
            String name = path.substring(slash + 1);
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String extension = dot > 0 ? name.substring(dot) : "";
            String candidate = path;
            for (int n = 2; !taken.add(candidate.toLowerCase(Locale.ROOT)); n++) {
                candidate = folder + base + " (" + n + ")" + extension;
            }
            return candidate;
        }

        private static void appendRow(StringBuilder csv, List<?> cells) {
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) {
                    csv.append(',');
                }
                csv.append(cell(cells.get(i)));
            }
            csv.append("\r\n");
        }

        /**
         * Một ô theo RFC 4180. Chữ bắt đầu bằng ký tự mà bảng tính hiểu là công thức được thêm dấu ' phía trước,
         * để mở tệp không vô tình chạy công thức (tệp hướng dẫn đọc có nêu điều này).
         */
        static String cell(Object value) {
            if (value == null) {
                return "";
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            String text = value.toString();
            if (looksLikeFormula(text)) {
                text = "'" + text;
            }
            if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0
                    || text.indexOf('\r') >= 0) {
                return '"' + text.replace("\"", "\"\"") + '"';
            }
            return text;
        }

        private static boolean looksLikeFormula(String text) {
            if (text.isEmpty()) {
                return false;
            }
            char first = text.charAt(0);
            if (first == '=' || first == '+' || first == '@' || first == '\t' || first == '\r') {
                return true;
            }
            return first == '-' && text.length() > 1 && !Character.isWhitespace(text.charAt(1))
                    && !Character.isDigit(text.charAt(1));
        }
    }
}
