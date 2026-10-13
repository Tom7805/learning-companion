package com.learningcompanion.shared.erasure;

import java.util.List;
import java.util.UUID;

/**
 * Mỗi chức năng giữ dữ liệu của người học ở ngoài các bảng có khóa ngoại tới tài khoản (tệp trong kho, chỉ mục
 * tìm kiếm, vector, bảng tra theo địa chỉ thư...) cài một eraser để phần đó bị xóa khi tài khoản bị xóa vĩnh viễn
 * (NCL-01-CN-009, QTN-35). Các dòng dữ liệu có khóa ngoại tới tài khoản tự mất theo tài khoản.
 *
 * <p>Eraser chạy trong giao dịch xóa tài khoản: chỉ xóa dòng trong cơ sở dữ liệu, còn tệp trong kho được ghi vào
 * {@code filesToDelete} để xóa sau khi giao dịch xong, nên lỗi giữa chừng không để lại bản ghi trỏ tới tệp đã mất.
 */
public interface PersonalDataEraser {

    /** Tên phần dữ liệu, ghi vào nhật ký và dấu vết tài khoản đã xóa. */
    String section();

    /** Xóa phần dữ liệu của tài khoản và trả về số mục đã xóa. */
    int erase(Subject subject, List<String> filesToDelete);

    /** Tài khoản đang bị xóa. */
    record Subject(UUID accountId, String email) {
    }
}
