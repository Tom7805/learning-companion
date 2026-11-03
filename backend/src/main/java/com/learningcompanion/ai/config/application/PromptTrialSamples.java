package com.learningcompanion.ai.config.application;

import com.learningcompanion.ai.safety.application.SourcePassage;
import java.util.List;
import java.util.Optional;

/**
 * Bộ nguồn mẫu để quản trị viên thử nhanh một phiên bản cấu hình trước khi phát hành (NCL-04-CN-002). Nguồn là đoạn
 * kiến thức phổ thông viết sẵn, không lấy từ tài liệu của người học nào (QTN-11): trang quản trị không bao giờ đọc nội
 * dung học tập.
 */
public final class PromptTrialSamples {

    public record Source(String title, String excerpt) {
    }

    public record SampleSet(String id, String title, String suggestedQuestion, List<Source> sources) {
    }

    public static final List<SampleSet> SETS = List.of(
            new SampleSet("physics", "Vật lý 11: Dòng điện không đổi", "Định luật Ohm cho đoạn mạch nói gì?", List.of(
                    new Source("Định luật Ohm",
                            "Cường độ dòng điện chạy qua một đoạn mạch chỉ có điện trở R tỉ lệ thuận với hiệu điện "
                                    + "thế U đặt vào hai đầu đoạn mạch và tỉ lệ nghịch với điện trở: I = U / R."),
                    new Source("Điện trở của dây dẫn",
                            "Điện trở của dây dẫn kim loại tỉ lệ thuận với chiều dài, tỉ lệ nghịch với tiết diện "
                                    + "và phụ thuộc vật liệu làm dây: R = ρ·l / S."),
                    new Source("Công suất điện",
                            "Công suất tiêu thụ của một đoạn mạch bằng tích hiệu điện thế và cường độ dòng điện: "
                                    + "P = U·I, đơn vị là oát (W)."))),
            new SampleSet("history", "Lịch sử 12: Kháng chiến chống Pháp", "Chiến dịch Điện Biên Phủ kết thúc khi nào?",
                    List.of(
                    new Source("Chiến dịch Điện Biên Phủ",
                            "Chiến dịch Điện Biên Phủ diễn ra từ ngày 13 tháng 3 đến ngày 7 tháng 5 năm 1954 và kết "
                                    + "thúc bằng thắng lợi của quân đội Việt Nam."),
                    new Source("Hiệp định Giơnevơ",
                            "Hiệp định Giơnevơ về Đông Dương được ký ngày 21 tháng 7 năm 1954, chấm dứt chiến tranh "
                                    + "và lấy vĩ tuyến 17 làm giới tuyến quân sự tạm thời."))),
            new SampleSet("biology", "Sinh học 11: Quang hợp", "Quang hợp diễn ra ở bào quan nào?", List.of(
                    new Source("Khái niệm quang hợp",
                            "Quang hợp là quá trình cây xanh dùng năng lượng ánh sáng để tổng hợp chất hữu cơ từ CO₂ "
                                    + "và nước, đồng thời giải phóng khí O₂."),
                    new Source("Lục lạp",
                            "Quang hợp diễn ra ở lục lạp. Pha sáng xảy ra trên màng thylakoid, pha tối (chu trình "
                                    + "Calvin) xảy ra trong chất nền stroma."))));

    private PromptTrialSamples() {
    }

    public static Optional<SampleSet> find(String id) {
        return SETS.stream().filter(set -> set.id().equals(id)).findFirst();
    }

    /** Nguồn mẫu dưới dạng đoạn nguồn, để đi qua lớp bao dữ liệu như tài liệu của người học (NCL-04-CN-004). */
    public static List<SourcePassage> passages(SampleSet set) {
        return set.sources().stream().map(source -> SourcePassage.of(source.title(), source.excerpt())).toList();
    }
}
