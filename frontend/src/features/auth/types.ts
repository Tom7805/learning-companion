export interface Account {
  id: string
  email: string
  displayName: string
  role: 'LEARNER' | 'ADMIN'
  status: 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'PENDING_DELETION'
  emailVerifiedAt?: string
}

/** Lý do trình duyệt không còn đăng nhập, để màn hình đăng nhập báo đúng. */
export type SessionLostReason = 'SESSION_EXPIRED' | 'SESSION_REVOKED'

export interface SessionStatus {
  authenticated: boolean
  account?: Account
  reason?: SessionLostReason
}

export interface RegisterPayload {
  displayName: string
  email: string
  password: string
  acceptTerms: boolean
  termsVersion: string
  privacyVersion: string
}

export interface RegistrationAccepted {
  email: string
  resendAvailableInSeconds: number
}

export interface ResendAccepted {
  resendAvailableInSeconds: number
}

/** Trạng thái màn hình "kiểm tra hộp thư", giữ qua lần tải lại trang. */
export interface PendingVerification {
  email: string
  resendAvailableAt: number
  /** Để điền lại biểu mẫu khi người học bấm "Đổi địa chỉ thư"; không bao giờ lưu mật khẩu. */
  displayName?: string
}

export interface LoginPayload {
  email: string
  password: string
  rememberDevice: boolean
}

export type DeviceType = 'DESKTOP' | 'MOBILE' | 'TABLET'

/** Một thiết bị đang đăng nhập. */
export interface DeviceSession {
  id: string
  browser: string
  operatingSystem: string
  deviceType?: DeviceType
  location?: string
  localNetwork: boolean
  signedInAt: string
  lastActiveAt: string
  expiresAt: string
  rememberDevice: boolean
  current: boolean
}

export interface RevokedByLink {
  browser: string
  operatingSystem: string
  signedInAt: string
  alreadyRevoked: boolean
}
