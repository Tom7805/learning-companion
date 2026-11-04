package com.learningcompanion.library.infrastructure.extractor;

import com.learningcompanion.library.domain.ChunkLocation;
import com.learningcompanion.library.domain.DocumentChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tách văn bản liền (lớp chữ của một trang, chữ nhận dạng từ hình, văn bản thuần) thành các khối: đoạn văn cách nhau
 * bằng dòng trống, đề mục kiểu Markdown hoặc dòng ngắn đứng riêng như "Chương 2", bảng các dòng bắt đầu bằng "|",
 * công thức giữa hai dòng "$$", mã giữa hai dòng "```".
 */
public final class Paragraphs {

    private static final Pattern BLANK_LINE = Pattern.compile("\\n[ \\t\\x0B\\f\\r]*\\n");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern NUMBERED_HEADING = Pattern.compile(
            "^(chương|chuong|bài|bai|phần|phan|mục|muc|chapter|section|part|lesson)\\s+[\\dIVXLC]+.*$|^\\d+(\\.\\d+)*\\.?\\s+\\S.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private Paragraphs() {
    }

    /** Khối của một vị trí cố định (một trang, một ảnh, một trang trình chiếu). */
    public static List<TextBlock> split(String text, ChunkLocation location, DocumentChunk.Source source) {
        List<TextBlock> blocks = new ArrayList<>();
        for (Piece piece : pieces(text, false)) {
            blocks.add(new TextBlock(location, piece.role(), piece.text(), source));
        }
        return blocks;
    }

    /**
     * Khối của văn bản thuần, mỗi khối là một đoạn văn được đánh số từ 1. Văn bản không có dòng trống nào thì mỗi dòng
     * là một đoạn văn.
     */
    public static List<TextBlock> numbered(String text) {
        List<TextBlock> blocks = new ArrayList<>();
        int number = 1;
        for (Piece piece : pieces(text, !BLANK_LINE.matcher(text).find())) {
            blocks.add(TextBlock.text(ChunkLocation.of(ChunkLocation.Kind.PARAGRAPH, number++), piece.role(),
                    piece.text()));
        }
        return blocks;
    }

    record Piece(TextBlock.Role role, String text) {
    }

    static List<Piece> pieces(String text, boolean linePerParagraph) {
        List<Piece> pieces = new ArrayList<>();
        if (text == null) {
            return pieces;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace('\u000c', '\n');
        String[] lines = normalized.split("\n", -1);
        List<String> paragraph = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.strip();
            if (trimmed.startsWith("```") || trimmed.equals("$$")) {
                flush(paragraph, pieces);
                String fence = trimmed.startsWith("```") ? "```" : "$$";
                List<String> body = new ArrayList<>();
                int j = i + 1;
                while (j < lines.length && !lines[j].strip().startsWith(fence)) {
                    body.add(lines[j]);
                    j++;
                }
                add(pieces, fence.equals("$$") ? TextBlock.Role.FORMULA : TextBlock.Role.CODE,
                        fence.equals("$$") ? "$$\n" + String.join("\n", body).strip() + "\n$$"
                                : String.join("\n", body));
                i = j;
                continue;
            }
            if (trimmed.startsWith("|")) {
                flush(paragraph, pieces);
                List<String> rows = new ArrayList<>();
                int j = i;
                while (j < lines.length && lines[j].strip().startsWith("|")) {
                    String row = lines[j].strip();
                    if (!row.replaceAll("[|:\\-\\s]", "").isEmpty()) {
                        rows.add(row.replaceAll("^\\|\\s*|\\s*\\|$", "").replaceAll("\\s*\\|\\s*", " | "));
                    }
                    j++;
                }
                add(pieces, TextBlock.Role.TABLE, String.join("\n", rows));
                i = j - 1;
                continue;
            }
            var heading = MARKDOWN_HEADING.matcher(trimmed);
            if (heading.matches()) {
                flush(paragraph, pieces);
                add(pieces, TextBlock.Role.HEADING, heading.group(1).strip());
                continue;
            }
            if (trimmed.isEmpty()) {
                flush(paragraph, pieces);
                continue;
            }
            paragraph.add(line.stripTrailing());
            if (linePerParagraph) {
                flush(paragraph, pieces);
            }
        }
        flush(paragraph, pieces);
        return pieces;
    }

    private static void flush(List<String> paragraph, List<Piece> pieces) {
        if (paragraph.isEmpty()) {
            return;
        }
        String text = String.join("\n", paragraph).strip();
        paragraph.clear();
        add(pieces, looksLikeHeading(text) ? TextBlock.Role.HEADING : TextBlock.Role.BODY, text);
    }

    private static void add(List<Piece> pieces, TextBlock.Role role, String text) {
        String clean = text == null ? "" : text.strip();
        if (!clean.isEmpty()) {
            pieces.add(new Piece(role, clean));
        }
    }

    /** Dòng ngắn đứng riêng, không kết thúc bằng dấu câu, và có dạng "Chương 2…", "1.2 …". */
    static boolean looksLikeHeading(String text) {
        if (text.contains("\n") || text.length() > 90) {
            return false;
        }
        char last = text.charAt(text.length() - 1);
        if (".,;:?!…)".indexOf(last) >= 0) {
            return false;
        }
        return NUMBERED_HEADING.matcher(text).matches();
    }

    /** Thu gọn khoảng trắng trong một dòng. */
    static String squash(String text) {
        return text.replaceAll("[ \\t\\x0B\\u00a0]+", " ").strip();
    }
}
