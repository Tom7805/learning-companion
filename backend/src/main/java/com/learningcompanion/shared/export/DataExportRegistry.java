package com.learningcompanion.shared.export;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Mọi contributor đang có trong ứng dụng, theo thứ tự xuất hiện trong gói. */
@Component
public class DataExportRegistry {

    private final List<DataExportContributor> contributors;

    public DataExportRegistry(List<DataExportContributor> contributors) {
        this.contributors = contributors.stream()
                .sorted(Comparator.comparingInt(DataExportContributor::order).thenComparing(DataExportContributor::section))
                .toList();
        long distinct = this.contributors.stream().map(DataExportContributor::section).distinct().count();
        if (distinct != this.contributors.size()) {
            throw new IllegalStateException("Hai phần trong gói xuất dùng chung một thư mục");
        }
    }

    public List<DataExportContributor> contributors() {
        return contributors;
    }
}
