package com.learningcompanion.devtools;

import com.learningcompanion.auth.domain.UserAccount;
import com.learningcompanion.auth.domain.UserAccountRepository;
import com.learningcompanion.library.domain.TagColor;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.workspace.domain.Workspace;
import com.learningcompanion.workspace.domain.WorkspaceIcon;
import com.learningcompanion.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dựng sẵn nhiều không gian học cho môi trường thử (NCL-06-CN-001 TC-01: người học đã có hai trăm không gian học).
 * Không bao giờ được bật ở môi trường thật.
 */
@RestController
@RequestMapping(ApiPaths.DEV + "/workspaces")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevWorkspaceController {

    private static final int MAX = 1000;

    private final UserAccountRepository accounts;
    private final WorkspaceRepository workspaces;
    private final Clock clock;

    public DevWorkspaceController(UserAccountRepository accounts, WorkspaceRepository workspaces, Clock clock) {
        this.accounts = accounts;
        this.workspaces = workspaces;
        this.clock = clock;
    }

    /** Tạo {@code count} không gian học tên "{prefix} 001", "{prefix} 002"…, xoay vòng biểu tượng và màu. */
    @PostMapping("/seed")
    @Transactional
    public SeedResponse seed(@RequestBody SeedRequest request) {
        UserAccount account = accounts.findByEmail(request.email() == null ? ""
                        : request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String prefix = request.prefix() == null || request.prefix().isBlank() ? "Không gian" : request.prefix().strip();
        WorkspaceIcon[] icons = WorkspaceIcon.values();
        TagColor[] colors = TagColor.values();
        // Lùi thời điểm tạo để không gian học tạo sau đó bằng giao diện luôn đứng đầu danh sách.
        Instant base = clock.instant().minusSeconds(3600);
        List<UUID> created = new ArrayList<>();
        for (int index = 1; index <= Math.clamp(request.count(), 0, MAX); index++) {
            created.add(workspaces.save(Workspace.create(account.getId(), "%s %03d".formatted(prefix, index),
                    icons[index % icons.length], colors[index % colors.length], "", base.plusSeconds(index))).getId());
        }
        return new SeedResponse(created);
    }

    public record SeedRequest(String email, int count, String prefix) {
    }

    public record SeedResponse(List<UUID> workspaces) {
    }
}
