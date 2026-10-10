package com.learningcompanion.auth.infrastructure;

import com.learningcompanion.shared.web.ClientInfo;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Vị trí ước lượng của một lần đăng nhập. Ưu tiên tiêu đề vị trí do proxy phía trước gắn vào
 * (ClientInfo.locationHint); không có thì chỉ nhận ra được mạng nội bộ. Muốn tra theo địa chỉ IP
 * công khai cần thêm cơ sở dữ liệu GeoIP ở lớp này.
 */
@Component
public class GeoIpLocator {

    /** Chỉ phân tích chuỗi trông như địa chỉ IP, để InetAddress không tra DNS. */
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F.:]+");

    public Location locate(ClientInfo client) {
        if (client.locationHint() != null) {
            return new Location(client.locationHint(), false);
        }
        return new Location(null, isLocalNetwork(client.ipAddress()));
    }

    static boolean isLocalNetwork(String ip) {
        if (ip == null || !IP_LITERAL.matcher(ip).matches()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public record Location(String description, boolean localNetwork) {
    }
}
