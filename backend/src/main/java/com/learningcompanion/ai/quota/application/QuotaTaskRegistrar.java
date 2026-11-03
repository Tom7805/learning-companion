package com.learningcompanion.ai.quota.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Khi ứng dụng khởi động, loại tác vụ tốn lượt mới được thêm vào bảng quy đổi với số lượt mặc định (NCL-04-CN-003).
 * Hai máy chủ khởi động cùng lúc thì một bên thắng nhờ khóa chính; bên kia bỏ qua.
 */
@Component
public class QuotaTaskRegistrar {

    private static final Logger log = LoggerFactory.getLogger(QuotaTaskRegistrar.class);

    private final QuotaService quotas;

    public QuotaTaskRegistrar(QuotaService quotas) {
        this.quotas = quotas;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        try {
            quotas.registerTasks();
        } catch (DataIntegrityViolationException ex) {
            log.info("Máy chủ khác vừa đăng ký bảng quy đổi lượt; bỏ qua lần này");
        }
    }
}
