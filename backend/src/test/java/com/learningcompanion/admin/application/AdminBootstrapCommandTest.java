package com.learningcompanion.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

/** Câu lệnh cài đặt tạo quản trị viên (NCL-03-CN-002): mã thoát cho từng trường hợp. */
class AdminBootstrapCommandTest {

    private final AdminProvisioningService provisioning = mock(AdminProvisioningService.class);
    private final AdminBootstrapCommand command = new AdminBootstrapCommand(provisioning,
            mock(ApplicationContext.class));

    @Test
    void createsTheAdmin() {
        assertThat(command.execute(Optional.of("quan.tri@example.com"), Optional.of("Quản trị"),
                Optional.of("mat-khau-dai-du-muoi"))).isZero();
        verify(provisioning).createAdmin("quan.tri@example.com", "Quản trị", "mat-khau-dai-du-muoi");
    }

    @Test
    void refusesWhenAnAdminAlreadyExists() {
        doThrow(new BusinessException(ErrorCode.ADMIN_ALREADY_EXISTS)).when(provisioning)
                .createAdmin(any(), any(), any());
        assertThat(command.execute(Optional.of("nguoi.thu.hai@example.com"), Optional.empty(),
                Optional.of("mat-khau-dai-du-muoi"))).isEqualTo(2);
    }

    @Test
    void needsEmailAndPassword() {
        assertThat(command.execute(Optional.empty(), Optional.empty(), Optional.of("x"))).isEqualTo(1);
        assertThat(command.execute(Optional.of("a@example.com"), Optional.empty(), Optional.empty())).isEqualTo(1);
        verify(provisioning, never()).createAdmin(any(), any(), any());
    }

    @Test
    void reportsInvalidPassword() {
        doThrow(new BusinessException(ErrorCode.PASSWORD_TOO_SHORT)).when(provisioning)
                .createAdmin(any(), any(), any());
        assertThat(command.execute(Optional.of("a@example.com"), Optional.empty(), Optional.of("ngan")))
                .isEqualTo(1);
    }
}
