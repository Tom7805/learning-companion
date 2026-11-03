package com.learningcompanion.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.learningcompanion.ai.config.application.ConfigDiffService;
import com.learningcompanion.ai.config.application.ConfigDiffService.Line;
import com.learningcompanion.ai.config.application.ConfigDiffService.LineChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigDiffServiceTest {

    private final ConfigDiffService diffs = new ConfigDiffService();

    @Test
    @DisplayName("Khác biệt theo dòng: giữ nguyên, bỏ và thêm đúng thứ tự")
    void diffsByLine() {
        assertThat(diffs.diffLines("Bạn là trợ lý.\nTrả lời ngắn.\nGhi nguồn.", "Bạn là trợ lý.\nTrả lời đầy đủ.\n"
                + "Ghi nguồn.\nDùng tiếng Việt."))
                .containsExactly(
                        new Line(LineChange.SAME, "Bạn là trợ lý."),
                        new Line(LineChange.REMOVED, "Trả lời ngắn."),
                        new Line(LineChange.ADDED, "Trả lời đầy đủ."),
                        new Line(LineChange.SAME, "Ghi nguồn."),
                        new Line(LineChange.ADDED, "Dùng tiếng Việt."));
    }

    @Test
    @DisplayName("Hai lời nhắc giống nhau thì mọi dòng giữ nguyên")
    void identical() {
        assertThat(diffs.diffLines("a\r\nb", "a\nb")).extracting(Line::change)
                .containsExactly(LineChange.SAME, LineChange.SAME);
    }
}
