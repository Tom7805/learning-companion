package com.learningcompanion.ai.config.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * So sánh lời nhắc hệ thống của hai phiên bản theo từng dòng (NCL-04-CN-002): dòng giữ nguyên, dòng bị bỏ và dòng thêm
 * mới, theo dãy con chung dài nhất. Lời nhắc giới hạn {@value AiConfigService#MAX_PROMPT_LENGTH} ký tự nên bảng quy
 * hoạch động vẫn nhỏ.
 */
@Component
public class ConfigDiffService {

    public enum LineChange { SAME, ADDED, REMOVED }

    public record Line(LineChange change, String text) {
    }

    public List<Line> diffLines(String before, String after) {
        String[] a = split(before);
        String[] b = split(after);
        int[][] common = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) {
            for (int j = b.length - 1; j >= 0; j--) {
                common[i][j] = a[i].equals(b[j]) ? common[i + 1][j + 1] + 1
                        : Math.max(common[i + 1][j], common[i][j + 1]);
            }
        }
        List<Line> lines = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < a.length && j < b.length) {
            if (a[i].equals(b[j])) {
                lines.add(new Line(LineChange.SAME, a[i]));
                i++;
                j++;
            } else if (common[i + 1][j] >= common[i][j + 1]) {
                lines.add(new Line(LineChange.REMOVED, a[i++]));
            } else {
                lines.add(new Line(LineChange.ADDED, b[j++]));
            }
        }
        while (i < a.length) {
            lines.add(new Line(LineChange.REMOVED, a[i++]));
        }
        while (j < b.length) {
            lines.add(new Line(LineChange.ADDED, b[j++]));
        }
        return lines;
    }

    private static String[] split(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        return text.replace("\r\n", "\n").split("\n", -1);
    }
}
