package com.learningcompanion.ai.config.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Khi ứng dụng khởi động, mỗi chức năng trí tuệ nhân tạo mới xây dựng được đăng ký phiên bản cấu hình đầu tiên
 * (NCL-04-CN-002). Hai máy chủ khởi động cùng lúc thì một bên thắng nhờ ràng buộc duy nhất; bên kia bỏ qua.
 */
@Component
public class AiFeatureRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AiFeatureRegistrar.class);

    private final AiConfigService configs;

    public AiFeatureRegistrar(AiConfigService configs) {
        this.configs = configs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        try {
            configs.registerFeatures();
        } catch (DataIntegrityViolationException ex) {
            log.info("Máy chủ khác vừa đăng ký cấu hình chức năng trí tuệ nhân tạo; bỏ qua lần này");
        }
    }
}
