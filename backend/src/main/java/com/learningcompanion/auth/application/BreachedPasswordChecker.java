package com.learningcompanion.auth.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Đối chiếu với danh sách mật khẩu phổ biến đã lộ, không phân biệt hoa thường.
 * Danh sách nằm trong mã nguồn nên không gửi mật khẩu ra dịch vụ bên ngoài.
 */
@Component
public class BreachedPasswordChecker {

    private static final String RESOURCE = "security/breached-passwords-top.txt";

    private final Set<String> breached;

    public BreachedPasswordChecker() {
        this.breached = load();
    }

    public boolean isBreached(String password) {
        return breached.contains(password.toLowerCase(Locale.ROOT));
    }

    private static Set<String> load() {
        Set<String> result = new HashSet<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.strip();
                if (!value.isEmpty() && !value.startsWith("#")) {
                    result.add(value.toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + RESOURCE, e);
        }
        return Set.copyOf(result);
    }
}
