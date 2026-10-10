/** Đọc ghi sessionStorage an toàn: trình duyệt chặn bộ nhớ (chế độ riêng tư) thì lặng lẽ bỏ qua. */
export const sessionStore = {
  get<T>(key: string): T | null {
    try {
      const raw = window.sessionStorage.getItem(key)
      return raw ? (JSON.parse(raw) as T) : null
    } catch {
      return null
    }
  },
  set(key: string, value: unknown) {
    try {
      window.sessionStorage.setItem(key, JSON.stringify(value))
    } catch {
      // Không lưu được thì trang vẫn chạy, chỉ mất khả năng khôi phục sau khi tải lại.
    }
  },
  remove(key: string) {
    try {
      window.sessionStorage.removeItem(key)
    } catch {
      // Bỏ qua như trên.
    }
  },
}
