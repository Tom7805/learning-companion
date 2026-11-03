package com.learningcompanion.ai.gateway;

import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Đọc khóa bí mật của nhà cung cấp từ kho bí mật của môi trường chạy (biến môi trường hoặc trình quản lý bí mật gắn
 * vào cấu hình Spring). Cơ sở dữ liệu chỉ giữ tên khóa; giá trị không bao giờ được lưu, trả về giao diện hay ghi nhật ký.
 */
@Component
public class AiSecretStore {

    /** Tên khóa: chữ in hoa, số và gạch dưới, ví dụ LC_AI_OPENAI_KEY. */
    public static final Pattern NAME = Pattern.compile("[A-Z][A-Z0-9_]{2,119}");

    private final Environment environment;

    public AiSecretStore(Environment environment) {
        this.environment = environment;
    }

    public String resolve(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            return null;
        }
        String value = environment.getProperty("app.ai.secrets." + name);
        if (value == null || value.isBlank()) {
            value = environment.getProperty(name);
        }
        return value == null || value.isBlank() ? null : value;
    }

    public boolean has(String name) {
        return resolve(name) != null;
    }
}
