package com.learningcompanion.admin.application;

import com.learningcompanion.admin.domain.RecoveryCode;
import com.learningcompanion.admin.domain.RecoveryCodeRepository;
import com.learningcompanion.shared.util.HashUtils;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mười mã khôi phục dùng một lần cho lúc mất điện thoại sinh mã (NCL-03-CN-002 TC-03). Mỗi mã gồm mười ký tự chữ
 * thường và số (bỏ các ký tự dễ nhầm), hiện dạng "xxxxx-xxxxx"; chỉ lưu băm nên không ai đọc lại được.
 */
@Service
public class RecoveryCodeService {

    public static final int CODE_COUNT = 10;
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int CODE_LENGTH = 10;

    private final RecoveryCodeRepository codes;
    private final SecureRandom random = new SecureRandom();

    public RecoveryCodeService(RecoveryCodeRepository codes) {
        this.codes = codes;
    }

    /** Bỏ mọi mã cũ và cấp bộ mới; trả về mã gốc để hiện cho quản trị viên đúng một lần. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> regenerate(UUID accountId, Instant now) {
        codes.deleteAllOfAccount(accountId);
        codes.flush();
        List<String> issued = new ArrayList<>(CODE_COUNT);
        while (issued.size() < CODE_COUNT) {
            String code = newCode();
            if (!issued.contains(code)) {
                issued.add(code);
                codes.save(RecoveryCode.issue(accountId, hash(code), now));
            }
        }
        return issued;
    }

    /** Dùng một mã chưa dùng: đánh dấu đã dùng và trả về true. Mã sai hoặc đã dùng trả về false. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean redeem(UUID accountId, String rawCode, Instant now) {
        Optional<RecoveryCode> found = codes.findByAccountIdAndCodeHash(accountId, hash(normalize(rawCode)));
        if (found.isEmpty() || found.get().isUsed()) {
            return false;
        }
        found.get().markUsed(now);
        return true;
    }

    @Transactional(readOnly = true)
    public long remaining(UUID accountId) {
        return codes.countByAccountIdAndUsedAtIsNull(accountId);
    }

    /** Người dùng có thể gõ hoa, bỏ dấu gạch hay thêm khoảng trắng. */
    static String normalize(String rawCode) {
        String compact = rawCode == null ? "" : rawCode.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return compact.length() == CODE_LENGTH
                ? compact.substring(0, CODE_LENGTH / 2) + "-" + compact.substring(CODE_LENGTH / 2) : compact;
    }

    private String newCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i == CODE_LENGTH / 2) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    private static String hash(String code) {
        return HashUtils.sha256Hex("admin-recovery:" + code);
    }
}
