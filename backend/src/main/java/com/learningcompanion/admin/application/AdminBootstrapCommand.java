package com.learningcompanion.admin.application;

import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.io.Console;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Câu lệnh cài đặt tạo quản trị viên duy nhất, chạy trên máy chủ (NCL-03-CN-002), xem {@code scripts/create-admin.sh}:
 *
 * <pre>
 * LC_ADMIN_PASSWORD='...' java -jar app.jar --server.port=0 \
 *     --create-admin --email=admin@example.com --name="Quản trị viên"   (hoặc biến LC_ADMIN_NAME)
 * </pre>
 *
 * Mật khẩu đọc từ biến môi trường {@code LC_ADMIN_PASSWORD} hoặc nhập ẩn từ bàn phím, không bao giờ qua tham số dòng
 * lệnh (sẽ lộ trong lịch sử shell và danh sách tiến trình). Đã có quản trị viên thì câu lệnh từ chối và thoát mã 2.
 */
@Component
public class AdminBootstrapCommand implements ApplicationRunner {

    static final String OPTION = "create-admin";
    static final String PASSWORD_ENV = "LC_ADMIN_PASSWORD";
    static final String NAME_ENV = "LC_ADMIN_NAME";
    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrapCommand.class);

    private final AdminProvisioningService provisioning;
    private final ApplicationContext context;

    public AdminBootstrapCommand(AdminProvisioningService provisioning, ApplicationContext context) {
        this.provisioning = provisioning;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(OPTION)) {
            return;
        }
        int exitCode = execute(first(args, "email"), first(args, "name").or(() -> Optional.ofNullable(System.getenv(NAME_ENV)).filter(n -> !n.isBlank())), password());
        System.exit(SpringApplication.exit(context, (ExitCodeGenerator) () -> exitCode));
    }

    /** Trả về mã thoát: 0 thành công, 1 dữ liệu không hợp lệ, 2 đã có quản trị viên. */
    int execute(Optional<String> email, Optional<String> name, Optional<String> password) {
        if (email.isEmpty() || password.isEmpty()) {
            LOG.error("Cần --email=<địa chỉ thư> và mật khẩu trong biến môi trường {}", PASSWORD_ENV);
            return 1;
        }
        try {
            provisioning.createAdmin(email.get(), name.orElse(null), password.get());
            LOG.info("Đã tạo tài khoản quản trị viên {}. Lần đăng nhập đầu tiên sẽ yêu cầu thiết lập xác thực hai bước.",
                    email.get());
            return 0;
        } catch (BusinessException rejected) {
            LOG.error("Không tạo được quản trị viên: {} ({})", rejected.getMessage(), rejected.code().name());
            return rejected.code() == ErrorCode.ADMIN_ALREADY_EXISTS ? 2 : 1;
        }
    }

    private static Optional<String> first(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() || values.get(0).isBlank()
                ? Optional.empty() : Optional.of(values.get(0).trim());
    }

    private static Optional<String> password() {
        String fromEnv = System.getenv(PASSWORD_ENV);
        if (fromEnv != null && !fromEnv.isEmpty()) {
            return Optional.of(fromEnv);
        }
        Console console = System.console();
        if (console == null) {
            return Optional.empty();
        }
        char[] typed = console.readPassword("Mật khẩu quản trị viên: ");
        return typed == null || typed.length == 0 ? Optional.empty() : Optional.of(new String(typed));
    }
}
