package com.learningcompanion.appshell.application;

import com.learningcompanion.appshell.domain.ThemeMode;
import com.learningcompanion.appshell.domain.UiPreferences;
import com.learningcompanion.appshell.domain.UiPreferencesRepository;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.ownership.OwnerContext;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tùy chọn giao diện của người học đang đăng nhập (NCL-02-CN-001). Chưa từng chọn thì trả mặc định mà không ghi gì;
 * lần đổi đầu tiên mới tạo bản ghi.
 */
@Service
public class UiPreferencesService {

    private final UiPreferencesRepository preferences;
    private final UserAccountRepository accounts;
    private final OwnerContext owner;
    private final Clock clock;

    public UiPreferencesService(UiPreferencesRepository preferences, UserAccountRepository accounts,
                                OwnerContext owner, Clock clock) {
        this.preferences = preferences;
        this.accounts = accounts;
        this.owner = owner;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public View mine() {
        UUID ownerId = owner.requireOwnerId();
        return preferences.findByOwnerId(ownerId).map(View::of).orElse(View.DEFAULTS);
    }

    /**
     * Đổi một hoặc vài tùy chọn; trường để trống giữ nguyên. Dòng tài khoản được khóa tới hết giao dịch để hai lần
     * đổi đầu tiên gửi cùng lúc (ví dụ bấm thu gọn thanh bên rồi đổi chế độ tối ngay) không tạo hai bản ghi.
     */
    @Transactional
    public View update(String themeMode, String fontSize, Boolean sidebarCollapsed) {
        UUID ownerId = owner.requireOwnerId();
        ThemeMode theme = parse(ThemeMode.class, themeMode, "themeMode");
        UiPreferences.FontSize font = parse(UiPreferences.FontSize.class, fontSize, "fontSize");
        accounts.lockById(ownerId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        UiPreferences current = preferences.findByOwnerId(ownerId)
                .orElseGet(() -> UiPreferences.withDefaults(ownerId, clock.instant()));
        current.change(theme, font, sidebarCollapsed, clock.instant());
        return View.of(preferences.save(current));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_PREFERENCE, field);
        }
    }

    public record View(ThemeMode themeMode, UiPreferences.FontSize fontSize, boolean sidebarCollapsed) {

        static final View DEFAULTS = new View(UiPreferences.DEFAULT_THEME, UiPreferences.DEFAULT_FONT_SIZE, false);

        static View of(UiPreferences preferences) {
            return new View(preferences.getThemeMode(), preferences.getFontSize(), preferences.isSidebarCollapsed());
        }
    }
}
