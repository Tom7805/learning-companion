/** Loại thông báo; loại mới do chức năng sau thêm vẫn hiển thị được dưới dạng chung. */
export type NotificationType = 'DATA_EXPORT_READY' | 'NEW_DEVICE_LOGIN' | 'PASSWORD_CHANGED'

export type NotificationTargetType = 'DATA_EXPORT' | 'DEVICES' | 'SECURITY'

/** Một thông báo trong ứng dụng (NCL-02-CN-002); tiêu đề được dựng từ loại và tham số theo ngôn ngữ đang dùng. */
export interface AppNotification {
  id: string
  type: NotificationType | (string & {})
  params: Record<string, unknown>
  targetType: NotificationTargetType | (string & {})
  createdAt: string
  /** Chưa đọc thì không có. */
  readAt?: string
}

export interface NotificationPage {
  items: AppNotification[]
  /** Mã thông báo cuối trang, dùng để tải trang cũ hơn; hết thì không có. */
  nextCursor?: string
  unreadCount: number
}

/** Kết quả mở một thông báo: đối tượng còn thì có đường dẫn, không còn thì {@code available} là false. */
export interface OpenedNotification {
  available: boolean
  path?: string
  unreadCount: number
}
