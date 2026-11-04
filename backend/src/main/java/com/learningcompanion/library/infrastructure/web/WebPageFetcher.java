package com.learningcompanion.library.infrastructure.web;

import java.net.URI;

/**
 * Lớp kết nối lấy nội dung trang web (QTN-04): thay được nhà cung cấp, môi trường thử dùng bản mô phỏng. Địa chỉ đưa vào
 * đã qua {@link WebAddressPolicy}; bản lấy thật còn kiểm lại mọi địa chỉ IP phân giải được và đích của mỗi lần chuyển
 * hướng.
 */
public interface WebPageFetcher {

    /**
     * @throws WebFetchException không lấy được nội dung
     * @throws BlockedAddressException tên miền hay đích chuyển hướng trỏ vào mạng nội bộ
     */
    FetchedPage fetch(URI uri);

    /** Trang đã lấy: đường dẫn cuối cùng sau chuyển hướng, kiểu nội dung, bảng mã (có thể trống) và nội dung thô. */
    record FetchedPage(URI finalUri, String contentType, String charset, byte[] body) {
    }
}
