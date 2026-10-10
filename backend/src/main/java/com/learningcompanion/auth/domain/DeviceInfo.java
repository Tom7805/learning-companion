package com.learningcompanion.auth.domain;

/**
 * Mô tả thiết bị của một phiên để người học nhận ra trong danh sách thiết bị.
 * `location` là vị trí ước lượng (có thể trống); `localNetwork` cho biết truy cập từ mạng nội bộ.
 */
public record DeviceInfo(String browser, String operatingSystem, DeviceType deviceType, String location,
                         boolean localNetwork) {

    public enum DeviceType { DESKTOP, MOBILE, TABLET }

    /** Ví dụ "Chrome trên Windows", dùng trong thư cảnh báo. */
    public String label() {
        return browser + " trên " + operatingSystem;
    }
}
