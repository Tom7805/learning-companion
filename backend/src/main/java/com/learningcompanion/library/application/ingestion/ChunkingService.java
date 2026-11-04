package com.learningcompanion.library.application.ingestion;

import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.DocumentChunk;
import com.learningcompanion.library.infrastructure.extractor.TextBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Chia văn bản đã trích thành đoạn theo đề mục và độ dài (NCL-05-CN-003):
 * <ul>
 * <li>đề mục mở một đoạn mới và được ghi kèm mọi đoạn bên dưới cho tới đề mục kế tiếp;</li>
 * <li>đoạn không vượt quá {@link #TARGET} ký tự, trừ khi một khối liền (bảng, công thức, mã) dài hơn; khối liền chỉ bị
 * cắt theo dòng khi dài quá {@link #MAX};</li>
 * <li>đoạn văn quá dài được cắt ở ranh giới câu;</li>
 * <li>đoạn không bao giờ trải qua hai trang, hai trang trình chiếu hay hai ảnh, nên luôn trỏ về đúng một vị trí
 * (QTN-20); với tài liệu Word, sách và văn bản thuần, một đoạn gộp được nhiều đoạn văn liền nhau.</li>
 * </ul>
 */
@Service
public class ChunkingService {

    public static final int TARGET = 1200;
    public static final int MAX = 2000;
    public static final int HEADING_MAX = 300;
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…])\\s+");

    /** Một đoạn sắp lưu, chưa có biểu diễn ngữ nghĩa. */
    public record Draft(ChunkLocation location, String heading, String content, DocumentChunk.Source source) {

        /** Văn bản đưa đi tạo biểu diễn ngữ nghĩa: đề mục giúp đoạn ngắn có ngữ cảnh. */
        public String embeddingText() {
            return heading == null ? content : heading + "\n" + content;
        }
    }

    public List<Draft> chunk(List<TextBlock> blocks) {
        Builder builder = new Builder();
        for (TextBlock block : blocks) {
            if (block.text().isEmpty()) {
                continue;
            }
            if (block.role() == TextBlock.Role.HEADING) {
                builder.heading(block);
            } else if (block.role().atomic()) {
                builder.atomic(block);
            } else {
                builder.body(block);
            }
        }
        builder.finish();
        return builder.drafts;
    }

    private static final class Builder {

        private final List<Draft> drafts = new ArrayList<>();
        private final List<TextBlock> pending = new ArrayList<>();
        private int length;
        private TextBlock heading;
        private boolean headingUsed = true;

        void heading(TextBlock block) {
            flush();
            orphanHeading();
            heading = block;
            headingUsed = false;
        }

        void atomic(TextBlock block) {
            breakIfIncompatible(block);
            if (block.text().length() <= MAX) {
                if (!pending.isEmpty() && length + block.text().length() > TARGET) {
                    flush();
                }
                add(block);
                return;
            }
            flush();
            for (String part : splitLines(block.text(), MAX)) {
                emit(block.location(), part, block.source());
            }
        }

        void body(TextBlock block) {
            breakIfIncompatible(block);
            String text = block.text();
            if (text.length() > TARGET) {
                flush();
                for (String part : splitSentences(text)) {
                    emit(block.location(), part, block.source());
                }
                return;
            }
            if (!pending.isEmpty() && length + text.length() > TARGET) {
                flush();
            }
            add(block);
        }

        void finish() {
            flush();
            orphanHeading();
        }

        private void breakIfIncompatible(TextBlock block) {
            if (pending.isEmpty()) {
                return;
            }
            TextBlock last = pending.getLast();
            boolean sameUnit = last.location().kind() == block.location().kind()
                    && (block.location().kind() == ChunkLocation.Kind.PARAGRAPH
                    || last.location().number() == block.location().number());
            if (!sameUnit || last.source() != block.source()) {
                flush();
            }
        }

        private void add(TextBlock block) {
            pending.add(block);
            length += block.text().length() + 2;
        }

        private void flush() {
            if (pending.isEmpty()) {
                return;
            }
            ChunkLocation location = pending.getFirst().location().through(pending.getLast().location());
            StringBuilder content = new StringBuilder();
            for (TextBlock block : pending) {
                if (!content.isEmpty()) {
                    content.append("\n\n");
                }
                content.append(block.text());
            }
            DocumentChunk.Source source = pending.getFirst().source();
            pending.clear();
            length = 0;
            emit(location, content.toString(), source);
        }

        private void emit(ChunkLocation location, String content, DocumentChunk.Source source) {
            String title = heading == null ? null : truncate(heading.text().replace('\n', ' '), HEADING_MAX);
            drafts.add(new Draft(location, title, content, source));
            headingUsed = true;
        }

        /** Đề mục không có nội dung bên dưới (ví dụ trang trình chiếu chỉ có tiêu đề) vẫn là một đoạn tìm được. */
        private void orphanHeading() {
            if (heading != null && !headingUsed) {
                drafts.add(new Draft(heading.location(), null, heading.text(), heading.source()));
                headingUsed = true;
            }
        }
    }

    static List<String> splitSentences(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : SENTENCE_END.split(text)) {
            if (sentence.length() > TARGET) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                parts.addAll(splitWords(sentence, TARGET));
                continue;
            }
            if (!current.isEmpty() && current.length() + 1 + sentence.length() > TARGET) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(sentence);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    static List<String> splitLines(String text, int limit) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.length() > limit) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                parts.addAll(splitWords(line, limit));
                continue;
            }
            if (!current.isEmpty() && current.length() + 1 + line.length() > limit) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static List<String> splitWords(String text, int limit) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + limit);
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > start + limit / 2) {
                    end = space;
                }
            }
            String part = text.substring(start, end).strip();
            if (!part.isEmpty()) {
                parts.add(part);
            }
            start = end;
        }
        return parts;
    }

    private static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit - 1).strip() + "…";
    }
}
