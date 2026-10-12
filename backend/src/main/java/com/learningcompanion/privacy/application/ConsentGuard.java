package com.learningcompanion.privacy.application;

import com.learningcompanion.privacy.domain.Consent;
import com.learningcompanion.privacy.domain.ConsentPurpose;
import com.learningcompanion.privacy.domain.ConsentRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cổng kiểm đồng ý cho mọi xử lý dữ liệu không bắt buộc: luôn đọc trạng thái mới nhất ngay lúc xử lý,
 * nên người học tắt một mục đích thì xử lý đó dừng ngay từ lần kế tiếp (QTN-03).
 */
@Component
public class ConsentGuard {

    private final ConsentRepository consents;

    public ConsentGuard(ConsentRepository consents) {
        this.consents = consents;
    }

    @Transactional(readOnly = true)
    public boolean allows(UUID userId, ConsentPurpose purpose) {
        return consents.findByUserIdAndPurpose(userId, purpose).map(Consent::isGranted).orElse(false);
    }
}
