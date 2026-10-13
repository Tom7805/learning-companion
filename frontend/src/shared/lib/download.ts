/** Lưu một tệp đã tải về bộ nhớ xuống máy người dùng mà không mở thẻ mới. */
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1_000)
}

/**
 * Tải tệp máy chủ trả về dạng đính kèm (Content-Disposition: attachment) qua một đường dẫn có hạn: trình duyệt
 * lưu tệp mà vẫn ở lại trang hiện tại.
 */
export function downloadFromUrl(url: string) {
  const link = document.createElement('a')
  link.href = url
  link.rel = 'noopener'
  document.body.append(link)
  link.click()
  link.remove()
}
