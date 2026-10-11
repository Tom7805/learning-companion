package com.learningcompanion.devtools;

import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.library.api.dto.DocumentCardResponse;
import com.learningcompanion.library.application.DocumentService;
import com.learningcompanion.library.domain.DocumentType;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.security.Role;
import com.learningcompanion.shared.web.ApiPaths;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dữ liệu mẫu cho môi trường thử (NCL-01-CN-004 TC-01): một bản ghi học tập kèm tệp gốc cho một tài khoản,
 * và cấp vai trò quản trị để thử điểm cuối quản trị. Không bao giờ được bật ở môi trường thật.
 */
@RestController
@RequestMapping(ApiPaths.DEV)
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevSampleDataController {

    private static final String SAMPLE_TEXT = """
            Định luật Ohm

            Cường độ dòng điện chạy qua một đoạn mạch tỉ lệ thuận với hiệu điện thế đặt vào hai đầu đoạn mạch \
            và tỉ lệ nghịch với điện trở của nó: I = U / R.

            Ví dụ: đặt hiệu điện thế 12 V vào một điện trở 4 Ω thì cường độ dòng điện là 12 / 4 = 3 A.

            Ghi nhớ:
            - Đơn vị của điện trở là ôm (Ω).
            - Khi giữ nguyên hiệu điện thế, tăng điện trở thì cường độ dòng điện giảm.
            - Đoạn mạch nối tiếp: R = R1 + R2; đoạn mạch song song: 1/R = 1/R1 + 1/R2.
            """;

    private final UserAccountRepository accounts;
    private final DocumentService documents;
    private final JdbcTemplate jdbc;

    public DevSampleDataController(UserAccountRepository accounts, DocumentService documents, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.documents = documents;
        this.jdbc = jdbc;
    }

    @PostMapping("/library/sample")
    public DocumentCardResponse sampleDocument(@RequestBody SampleRequest request) {
        UserAccount account = account(request.email());
        String title = request.title() == null || request.title().isBlank()
                ? "Ghi chú: Định luật Ohm" : request.title().trim();
        return DocumentCardResponse.of(documents.create(account.getId(), title, DocumentType.TEXT, SAMPLE_TEXT,
                "dinh-luat-ohm.txt", "text/plain; charset=utf-8", SAMPLE_TEXT.getBytes(StandardCharsets.UTF_8)));
    }

    @PostMapping("/accounts/role")
    public ResponseEntity<Void> setRole(@RequestBody RoleRequest request) {
        UserAccount account = account(request.email());
        jdbc.update("update user_accounts set role = ? where id = ?", request.role().name(), account.getId());
        return ResponseEntity.noContent().build();
    }

    private UserAccount account(String email) {
        return accounts.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public record SampleRequest(String email, String title) {
    }

    public record RoleRequest(String email, Role role) {
    }
}
