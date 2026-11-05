package com.learningcompanion.library.infrastructure.web;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lấy nội dung trang web cho kho tài liệu (NCL-05-CN-004, QTN-04). {@code mode}: {@code http} gọi trang thật,
 * {@code mock} trả các trang mẫu cố định (máy phát triển, kiểm thử). {@code connectTimeout} và {@code timeout} giới
 * hạn thời gian chờ kết nối và tổng thời gian lấy một trang kể cả đọc nội dung; {@code maxBytes} là cỡ tối đa của trang;
 * {@code maxRedirects} là số lần chuyển hướng tối đa, mỗi lần đều kiểm lại địa chỉ đích.
 */
@ConfigurationProperties(prefix = "app.library.web-fetch")
public record WebFetchProperties(String mode, Duration connectTimeout, Duration timeout, Integer maxBytes,
                                 Integer maxRedirects) {

    public WebFetchProperties {
        if (mode == null || mode.isBlank()) {
            mode = "http";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(15);
        }
        if (maxBytes == null || maxBytes <= 0) {
            maxBytes = 3 * 1024 * 1024;
        }
        if (maxRedirects == null || maxRedirects < 0) {
            maxRedirects = 5;
        }
    }
}
