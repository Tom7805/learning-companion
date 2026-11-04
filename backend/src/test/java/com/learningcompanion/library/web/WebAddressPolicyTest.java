package com.learningcompanion.library.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learningcompanion.library.infrastructure.web.BlockedAddressException;
import com.learningcompanion.library.infrastructure.web.WebAddressPolicy;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** NCL-05-CN-004 TC-03 — chống SSRF: chỉ trang công khai qua http/https mới được lấy. */
class WebAddressPolicyTest {

    private final WebAddressPolicy policy = new WebAddressPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"https://vi.wikipedia.org/wiki/Thuật_toán_sắp_xếp", "http://example.com/a?b=c",
        "https://cafe.be/", "https://8.8.8.8/", "https://[2606:4700:4700::1111]/", "https://bai-viet.example:8443/x"})
    @DisplayName("Đường dẫn công khai qua http/https được nhận")
    void acceptsPublicAddresses(String url) {
        assertThat(policy.parse(url).getScheme()).startsWith("http");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost/", "http://LOCALHOST:8080/admin", "http://127.0.0.1/",
        "http://127.1.2.3/", "http://10.0.0.5/", "http://172.16.3.4/", "http://172.31.255.255/",
        "http://192.168.1.1/", "http://169.254.169.254/latest/meta-data/", "http://100.64.0.1/", "http://0.0.0.0/",
        "http://[::1]/", "http://[fd00::1]/", "http://[fe80::1]/", "http://[::ffff:127.0.0.1]/",
        "http://[::ffff:10.0.0.1]/", "http://[64:ff9b::a9fe:a9fe]/", "http://2130706433/", "http://0x7f000001/",
        "http://017700000001/", "http://intranet/", "http://router.local/",
        "http://db.internal/", "http://printer.lan/", "http://app.localhost/", "http://224.0.0.1/"})
    @DisplayName("Địa chỉ nội bộ, chính máy chủ hay dải dành riêng bị chặn")
    void blocksInternalAddresses(String url) {
        assertThatThrownBy(() -> policy.parse(url)).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo(ErrorCode.WEB_ADDRESS_BLOCKED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "trang web", "ftp://example.com/a", "file:///etc/passwd", "javascript:alert(1)",
        "gopher://example.com/", "https://user:mat-khau@example.com/", "https://example.com:99999/", "//example.com/",
        "https://exa mple.com/", "http://127.1/"})
    @DisplayName("Đường dẫn sai dạng hay giao thức khác http/https bị từ chối")
    void rejectsInvalidAddresses(String url) {
        assertThatThrownBy(() -> policy.parse(url)).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo(ErrorCode.WEB_ADDRESS_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.1.2.3", "192.168.0.10", "127.0.0.1", "::1", "fc00::5", "169.254.1.1", "198.18.0.1"})
    @DisplayName("Địa chỉ đã phân giải thuộc mạng nội bộ bị chặn (tên miền trỏ vào trong)")
    void blocksResolvedInternalAddresses(String ip) throws Exception {
        InetAddress address = InetAddress.getByName(ip);
        assertThatThrownBy(() -> policy.checkAddress(address)).isInstanceOf(BlockedAddressException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.216.34", "1.1.1.1", "2606:4700:4700::1111"})
    @DisplayName("Địa chỉ đã phân giải công khai được nhận")
    void acceptsResolvedPublicAddresses(String ip) throws Exception {
        InetAddress address = InetAddress.getByName(ip);
        assertThatCode(() -> policy.checkAddress(address)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1/a", "ftp://example.com/"})
    @DisplayName("Đích chuyển hướng được kiểm như đường dẫn dán vào")
    void checksRedirectTargets(String url) {
        assertThatThrownBy(() -> policy.check(URI.create(url))).isInstanceOfAny(BlockedAddressException.class,
                BusinessException.class);
    }
}
