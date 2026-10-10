package com.learningcompanion.auth.infrastructure;

import com.learningcompanion.auth.domain.DeviceInfo;
import org.springframework.stereotype.Component;

/**
 * Nhận diện trình duyệt, hệ điều hành và loại thiết bị từ User-Agent, đủ để người học nhận ra thiết bị
 * của mình trong danh sách. Thứ tự kiểm tra quan trọng vì nhiều trình duyệt tự xưng cả "Chrome" lẫn "Safari".
 */
@Component
public class UserAgentParser {

    private static final String UNKNOWN = "Không rõ";

    public Parsed parse(String userAgent) {
        String ua = userAgent == null ? "" : userAgent;
        return new Parsed(browser(ua), operatingSystem(ua), deviceType(ua));
    }

    private static String browser(String ua) {
        if (ua.contains("coc_coc_browser")) {
            return "Cốc Cốc";
        }
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("SamsungBrowser/")) {
            return "Samsung Internet";
        }
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            return "Firefox";
        }
        if (ua.contains("CriOS/") || ua.contains("Chrome/") || ua.contains("HeadlessChrome/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/")) {
            return "Safari";
        }
        return UNKNOWN;
    }

    private static String operatingSystem(String ua) {
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("iPhone")) {
            return "iOS";
        }
        if (ua.contains("iPad")) {
            return "iPadOS";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return UNKNOWN;
    }

    private static DeviceInfo.DeviceType deviceType(String ua) {
        if (ua.contains("iPad") || ua.contains("Tablet") || (ua.contains("Android") && !ua.contains("Mobile"))) {
            return DeviceInfo.DeviceType.TABLET;
        }
        if (ua.contains("Mobi") || ua.contains("iPhone")) {
            return DeviceInfo.DeviceType.MOBILE;
        }
        return DeviceInfo.DeviceType.DESKTOP;
    }

    public record Parsed(String browser, String operatingSystem, DeviceInfo.DeviceType deviceType) {
    }
}
