/**
 * Tải lại toàn bộ ứng dụng ở một trang khác, bỏ mọi trạng thái trong bộ nhớ. Dùng khi phiên vừa bị thu hồi ở máy
 * chủ (ví dụ sau khi yêu cầu xóa tài khoản), để không màn hình nào kịp dựng lại với phiên cũ.
 */
export function reloadTo(path: string) {
  window.location.replace(path)
}

/** Tải lại trang đang xem, ví dụ khi gói mã của trang không còn sau khi có bản mới. */
export function reloadPage() {
  window.location.reload()
}
