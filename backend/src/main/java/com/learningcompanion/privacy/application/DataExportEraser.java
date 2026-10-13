package com.learningcompanion.privacy.application;

import com.learningcompanion.privacy.domain.DataExportRequest;
import com.learningcompanion.privacy.domain.DataExportRequestRepository;
import com.learningcompanion.privacy.domain.DataExportStatus;
import com.learningcompanion.shared.erasure.PersonalDataEraser;
import java.util.List;
import org.springframework.stereotype.Component;

/** Gói dữ liệu đã xuất khi xóa tài khoản: tệp gói còn hạn bị xóa khỏi kho; dòng lần xuất mất theo khóa ngoại. */
@Component
public class DataExportEraser implements PersonalDataEraser {

    private final DataExportRequestRepository exports;

    public DataExportEraser(DataExportRequestRepository exports) {
        this.exports = exports;
    }

    @Override
    public String section() {
        return "exports";
    }

    @Override
    public int erase(Subject subject, List<String> filesToDelete) {
        List<DataExportRequest> ready = exports.findAllByOwnerIdAndStatus(subject.accountId(), DataExportStatus.READY);
        ready.forEach(export -> filesToDelete.add(export.getStorageKey()));
        return ready.size();
    }
}
