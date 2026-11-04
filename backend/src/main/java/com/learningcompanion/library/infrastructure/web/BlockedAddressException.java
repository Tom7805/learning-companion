package com.learningcompanion.library.infrastructure.web;

/** Địa chỉ thuộc mạng nội bộ, chính máy chủ hay dải dành riêng: không bao giờ được lấy nội dung (chống SSRF). */
public class BlockedAddressException extends RuntimeException {

    public BlockedAddressException(String host) {
        super("Địa chỉ bị chặn: " + host);
    }
}
