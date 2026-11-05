package com.learningcompanion.library.infrastructure.web;

import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Chống SSRF khi lấy trang web (NCL-05-CN-004 TC-03): chỉ nhận http/https trên cổng hợp lệ, không có thông tin đăng
 * nhập trong đường dẫn, và chặn mọi địa chỉ thuộc chính máy chủ, mạng nội bộ, liên kết cục bộ, dải dùng chung của nhà
 * mạng, dải dành riêng hay phát nhóm, cả IPv4 lẫn IPv6 (kể cả IPv4 nhúng trong IPv6). Tên máy chỉ có ý nghĩa nội bộ
 * (localhost, .local, .internal…) bị chặn ngay; tên miền thường được kiểm sau khi phân giải.
 */
@Component
public class WebAddressPolicy {

    public static final int URL_MAX_LENGTH = 2048;

    private static final Pattern DOTTED_QUAD = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    // Dạng số khác của IPv4 (thập phân liền, bát phân, thập lục phân) mà một số bộ phân giải vẫn hiểu.
    private static final Pattern NUMERIC_HOST = Pattern.compile("(0x[0-9a-f]*|\\d+)(\\.(0x[0-9a-f]*|\\d+)){0,3}");
    private static final String[] INTERNAL_SUFFIXES = {".localhost", ".local", ".internal", ".intranet", ".lan",
        ".home", ".corp", ".home.arpa", ".localdomain"};

    private final boolean allowPrivate;

    @Autowired
    public WebAddressPolicy() {
        this(false);
    }

    /** {@code allowPrivate}: chỉ dùng trong kiểm thử bản lấy thật với máy chủ chạy trên chính máy kiểm thử. */
    public WebAddressPolicy(boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    /** Đọc và kiểm đường dẫn người học dán vào; trả lỗi nghiệp vụ dễ hiểu khi không hợp lệ hay bị chặn. */
    public URI parse(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty() || text.length() > URL_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.WEB_ADDRESS_INVALID);
        }
        URI uri;
        try {
            uri = new URI(text).normalize();
        } catch (URISyntaxException ex) {
            throw new BusinessException(ErrorCode.WEB_ADDRESS_INVALID);
        }
        try {
            check(uri);
        } catch (BlockedAddressException ex) {
            throw new BusinessException(ErrorCode.WEB_ADDRESS_BLOCKED);
        }
        return uri;
    }

    /**
     * Kiểm một đường dẫn (cũng dùng cho đích chuyển hướng) không cần phân giải tên miền.
     *
     * @throws BusinessException đường dẫn sai dạng
     * @throws BlockedAddressException địa chỉ nội bộ
     */
    public void check(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BusinessException(ErrorCode.WEB_ADDRESS_INVALID);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || uri.getRawUserInfo() != null) {
            throw new BusinessException(ErrorCode.WEB_ADDRESS_INVALID);
        }
        int port = uri.getPort();
        if (port == 0 || port > 65535) {
            throw new BusinessException(ErrorCode.WEB_ADDRESS_INVALID);
        }
        checkHostName(host);
    }

    void checkHostName(String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (allowPrivate) {
            return;
        }
        if (host.equals("localhost")) {
            throw new BlockedAddressException(rawHost);
        }
        for (String suffix : INTERNAL_SUFFIXES) {
            if (host.endsWith(suffix)) {
                throw new BlockedAddressException(rawHost);
            }
        }
        if (host.startsWith("[") || DOTTED_QUAD.matcher(host).matches()) {
            checkAddress(literal(host, rawHost));
        } else if (!host.contains(".") || NUMERIC_HOST.matcher(host).matches()) {
            // Tên một chữ (máy trong mạng nội bộ) hay dạng số lạ của IPv4: không bao giờ là trang công khai.
            throw new BlockedAddressException(rawHost);
        }
    }

    /** Kiểm một địa chỉ đã phân giải. */
    public void checkAddress(InetAddress address) {
        if (allowPrivate) {
            return;
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            throw new BlockedAddressException(address.getHostAddress());
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            if (blockedIpv4(bytes)) {
                throw new BlockedAddressException(address.getHostAddress());
            }
        } else if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            // fc00::/7 (địa chỉ cục bộ duy nhất), fec0::/10 (site-local cũ), 2001:db8::/32 (tài liệu).
            boolean uniqueLocal = (first & 0xfe) == 0xfc;
            boolean oldSiteLocal = first == 0xfe && (bytes[1] & 0xc0) == 0xc0;
            boolean documentation = first == 0x20 && bytes[1] == 0x01 && (bytes[2] & 0xff) == 0x0d
                    && (bytes[3] & 0xff) == 0xb8;
            if (uniqueLocal || oldSiteLocal || documentation) {
                throw new BlockedAddressException(address.getHostAddress());
            }
            if (embedsIpv4(bytes) && blockedIpv4(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]})) {
                throw new BlockedAddressException(address.getHostAddress());
            }
        }
    }

    private static boolean blockedIpv4(byte[] b) {
        int a = b[0] & 0xff;
        int c = b[1] & 0xff;
        int d = b[2] & 0xff;
        return a == 0 || a == 10 || a == 127 || a >= 224
                || (a == 100 && c >= 64 && c <= 127)
                || (a == 169 && c == 254)
                || (a == 172 && c >= 16 && c <= 31)
                || (a == 192 && c == 168)
                || (a == 192 && c == 0 && (d == 0 || d == 2))
                || (a == 198 && (c == 18 || c == 19))
                || (a == 198 && c == 51 && d == 100)
                || (a == 203 && c == 0 && d == 113);
    }

    /** IPv4 nhúng trong IPv6: ::a.b.c.d, ::ffff:a.b.c.d và 64:ff9b::/96 (NAT64). */
    private static boolean embedsIpv4(byte[] b) {
        boolean zeroPrefix = true;
        for (int i = 0; i < 10; i++) {
            zeroPrefix &= b[i] == 0;
        }
        boolean mappedOrCompat = zeroPrefix
                && ((b[10] == 0 && b[11] == 0) || (b[10] == (byte) 0xff && b[11] == (byte) 0xff));
        boolean nat64 = b[0] == 0 && b[1] == 0x64 && b[2] == (byte) 0xff && b[3] == (byte) 0x9b;
        for (int i = 4; nat64 && i < 12; i++) {
            nat64 = b[i] == 0;
        }
        return mappedOrCompat || nat64;
    }

    private static InetAddress literal(String host, String rawHost) {
        try {
            // Địa chỉ dạng số không cần tra DNS.
            return InetAddress.getByName(host.startsWith("[") ? host.substring(1, host.length() - 1) : host);
        } catch (UnknownHostException ex) {
            throw new BlockedAddressException(rawHost);
        }
    }
}
