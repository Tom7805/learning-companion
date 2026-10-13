package com.learningcompanion.privacy.api.dto;

import jakarta.validation.constraints.Size;

/** Mật khẩu xác nhận lại danh tính; để trống khi vừa đăng nhập lại trong năm phút qua. */
public record DataExportDownloadRequest(@Size(max = 128) String password) {
}
