package com.learningcompanion.ai.labeling;

import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thành phần dùng chung gắn nhãn nội dung do trí tuệ nhân tạo sinh ra (NCL-04-CN-005, QTN-23). Mọi chức năng sinh học
 * liệu về sau đi qua đây: thông báo đang trò chuyện với hệ thống trí tuệ nhân tạo (lần đầu mở trợ lý), và tệp tải về
 * luôn mang dấu nhận biết máy đọc được; không gắn được dấu thì chặn tải bằng lỗi tạm thời.
 */
@Service
public class AiLabelService {

    /** Nhãn hiển thị và ghi trong siêu dữ liệu. */
    public static final String LABEL = "Nội dung do trí tuệ nhân tạo sinh ra";

    /** Phiên bản nội dung thông báo; đổi lời thông báo thì tăng để mọi người học đọc lại. */
    public static final int DISCLOSURE_VERSION = 1;

    private static final Logger log = LoggerFactory.getLogger(AiLabelService.class);

    private final AiDisclosureAcknowledgementRepository acknowledgements;
    private final UserAccountRepository accounts;
    private final MachineReadableWatermark watermark;
    private final OwnerContext owner;
    private final Clock clock;

    public AiLabelService(AiDisclosureAcknowledgementRepository acknowledgements, UserAccountRepository accounts,
                          MachineReadableWatermark watermark, OwnerContext owner, Clock clock) {
        this.acknowledgements = acknowledgements;
        this.accounts = accounts;
        this.watermark = watermark;
        this.owner = owner;
        this.clock = clock;
    }

    /** Người học đang đăng nhập đã đọc thông báo hiện hành chưa, và đọc lúc nào. */
    public record Disclosure(int version, boolean acknowledged, Instant acknowledgedAt) {
    }

    @Transactional(readOnly = true)
    public Disclosure disclosure() {
        UUID ownerId = owner.requireOwnerId();
        return acknowledgements.findByOwnerId(ownerId)
                .filter(found -> found.getVersion() >= DISCLOSURE_VERSION)
                .map(found -> new Disclosure(DISCLOSURE_VERSION, true, found.getAcknowledgedAt()))
                .orElse(new Disclosure(DISCLOSURE_VERSION, false, null));
    }

    /**
     * Ghi nhận đã đọc thông báo. Dòng tài khoản được khóa tới hết giao dịch để hai thẻ cùng bấm "Đã hiểu" vẫn chỉ tạo
     * một dòng.
     */
    @Transactional
    public Disclosure acknowledge() {
        UUID ownerId = owner.requireOwnerId();
        accounts.lockById(ownerId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        Instant now = clock.instant();
        AiDisclosureAcknowledgement current = acknowledgements.findByOwnerId(ownerId)
                .orElseGet(() -> AiDisclosureAcknowledgement.of(ownerId, DISCLOSURE_VERSION, now));
        if (current.getVersion() < DISCLOSURE_VERSION) {
            current.acknowledge(DISCLOSURE_VERSION, now);
        }
        AiDisclosureAcknowledgement saved = acknowledgements.save(current);
        return new Disclosure(DISCLOSURE_VERSION, true, saved.getAcknowledgedAt());
    }

    /**
     * Tệp văn bản định dạng nhẹ của một nội dung do trí tuệ nhân tạo sinh ra: khối siêu dữ liệu có dấu nhận biết, dòng
     * nhãn đọc được bằng mắt, rồi nội dung. Bộ đính dấu lỗi thì ném {@link ErrorCode#AI_LABEL_UNAVAILABLE} (TC-04).
     */
    public String markdown(String title, String body, AiProvenance provenance) {
        String visible = "> " + LABEL + (provenance.edited() ? " · Đã chỉnh sửa" : "")
                + ". Hãy đối chiếu với tài liệu gốc trước khi sử dụng.\n\n" + body;
        try {
            return watermark.apply(title, visible, provenance);
        } catch (MachineReadableWatermark.WatermarkException ex) {
            log.warn("Không gắn được dấu nhận biết cho nội dung {}: {}", provenance.contentId(), ex.getMessage());
            throw new BusinessException(ErrorCode.AI_LABEL_UNAVAILABLE);
        }
    }
}
