package com.learningcompanion.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Việc định kỳ của hệ thống, ví dụ dọn gói dữ liệu đã hết hạn tải. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
