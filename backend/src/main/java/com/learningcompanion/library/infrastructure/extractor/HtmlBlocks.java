package com.learningcompanion.library.infrastructure.extractor;

import com.learningcompanion.library.domain.ChunkLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc khối văn bản từ HTML/XHTML một cách dễ dãi (sách điện tử, trang web): không cần HTML hợp chuẩn, bỏ qua script,
 * style và phần đầu trang. h1–h6 là đề mục; bảng giữ hàng và ô; pre là mã; công thức MathML giữ ký hiệu. Mỗi khối là
 * một đoạn văn được đánh số tiếp theo {@code next}.
 */
final class HtmlBlocks {

    private static final Pattern TOKEN = Pattern.compile("<!--.*?-->|<!\\[CDATA\\[(.*?)]]>|<(/?)([a-zA-Z][a-zA-Z0-9:-]*)([^>]*)>|<![^>]*>|<\\?[^>]*\\?>",
            Pattern.DOTALL);
    private static final Set<String> BLOCKS = Set.of("p", "div", "li", "blockquote", "section", "article", "dd",
            "dt", "figcaption", "caption", "br", "hr", "ul", "ol", "dl", "body", "header", "footer", "aside", "main",
            "nav", "figure");
    private static final Set<String> SKIP = Set.of("script", "style", "head", "title", "noscript", "template", "svg");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");

    private final List<TextBlock> blocks;
    private final StringBuilder text = new StringBuilder();
    private TextBlock.Role role = TextBlock.Role.BODY;
    private int skipDepth;
    private int tableDepth;
    private int preDepth;
    private int mathDepth;
    private final List<String> rows = new ArrayList<>();
    private final List<String> cells = new ArrayList<>();
    private final StringBuilder cell = new StringBuilder();

    private HtmlBlocks(List<TextBlock> blocks) {
        this.blocks = blocks;
    }

    /** Thêm các khối của một tệp HTML vào {@code blocks}; số đoạn văn tiếp nối các khối đã có. */
    static void read(String html, List<TextBlock> blocks) {
        HtmlBlocks reader = new HtmlBlocks(blocks);
        Matcher matcher = TOKEN.matcher(html);
        int last = 0;
        while (matcher.find()) {
            reader.characters(html.substring(last, matcher.start()));
            if (matcher.group(1) != null) {
                reader.characters(matcher.group(1));
            } else if (matcher.group(3) != null) {
                String name = matcher.group(3).toLowerCase(Locale.ROOT);
                int colon = name.indexOf(':');
                if (colon >= 0) {
                    name = name.substring(colon + 1);
                }
                boolean closing = !matcher.group(2).isEmpty();
                boolean selfClosing = matcher.group(4).endsWith("/");
                if (closing) {
                    reader.end(name);
                } else {
                    reader.start(name);
                    if (selfClosing) {
                        reader.end(name);
                    }
                }
            }
            last = matcher.end();
        }
        reader.characters(html.substring(last));
        reader.flush();
    }

    private void start(String name) {
        if (SKIP.contains(name)) {
            skipDepth++;
            return;
        }
        if (skipDepth > 0) {
            return;
        }
        switch (name) {
            case "table" -> {
                if (tableDepth++ == 0) {
                    flush();
                    rows.clear();
                }
            }
            case "tr" -> cells.clear();
            case "td", "th" -> cell.setLength(0);
            case "pre" -> {
                flush();
                preDepth++;
                role = TextBlock.Role.CODE;
            }
            case "math" -> {
                if (mathDepth++ == 0 && tableDepth == 0) {
                    text.append(' ');
                }
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flush();
                role = TextBlock.Role.HEADING;
            }
            default -> {
                if (BLOCKS.contains(name) && tableDepth == 0 && preDepth == 0) {
                    if ("br".equals(name)) {
                        text.append('\n');
                    } else {
                        flush();
                    }
                }
            }
        }
    }

    private void end(String name) {
        if (SKIP.contains(name)) {
            skipDepth = Math.max(0, skipDepth - 1);
            return;
        }
        if (skipDepth > 0) {
            return;
        }
        switch (name) {
            case "td", "th" -> {
                if (tableDepth == 1) {
                    cells.add(Paragraphs.squash(cell.toString()));
                }
            }
            case "tr" -> {
                if (tableDepth == 1 && cells.stream().anyMatch(value -> !value.isEmpty())) {
                    rows.add(String.join(" | ", cells));
                }
            }
            case "table" -> {
                if (--tableDepth <= 0) {
                    tableDepth = 0;
                    if (!rows.isEmpty()) {
                        add(TextBlock.Role.TABLE, String.join("\n", rows));
                    }
                }
            }
            case "pre" -> {
                preDepth = Math.max(0, preDepth - 1);
                flush();
            }
            case "math" -> mathDepth = Math.max(0, mathDepth - 1);
            case "h1", "h2", "h3", "h4", "h5", "h6" -> flush();
            default -> {
                if (BLOCKS.contains(name) && tableDepth == 0 && preDepth == 0) {
                    flush();
                }
            }
        }
    }

    private void characters(String raw) {
        if (skipDepth > 0 || raw.isEmpty()) {
            return;
        }
        String decoded = decode(raw);
        if (tableDepth > 0) {
            cell.append(decoded);
        } else if (preDepth > 0) {
            text.append(decoded);
        } else {
            text.append(decoded.replaceAll("\\s+", " "));
        }
    }

    private void flush() {
        String value = preDepth > 0 || role == TextBlock.Role.CODE ? text.toString().strip()
                : text.toString().lines().map(Paragraphs::squash).filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + "\n" + b).orElse("");
        text.setLength(0);
        add(role, value);
        role = TextBlock.Role.BODY;
    }

    private void add(TextBlock.Role blockRole, String value) {
        if (!value.isBlank()) {
            blocks.add(TextBlock.text(ChunkLocation.of(ChunkLocation.Kind.PARAGRAPH, blocks.size() + 1), blockRole,
                    value));
        }
    }

    static String decode(String text) {
        if (text.indexOf('&') < 0) {
            return text;
        }
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String entity = matcher.group(1);
            String replacement = switch (entity) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                case "ndash" -> "–";
                case "mdash" -> "—";
                case "hellip" -> "…";
                default -> {
                    if (entity.startsWith("#")) {
                        try {
                            int code = entity.startsWith("#x") || entity.startsWith("#X")
                                    ? Integer.parseInt(entity.substring(2), 16) : Integer.parseInt(entity.substring(1));
                            yield Character.isValidCodePoint(code) ? new String(Character.toChars(code)) : "";
                        } catch (NumberFormatException ex) {
                            yield matcher.group();
                        }
                    }
                    yield matcher.group();
                }
            };
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
