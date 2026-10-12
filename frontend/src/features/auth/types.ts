export interface Account {
  id: string
  email: string
  displayName: string
  role: 'LEARNER' | 'ADMIN'
  status: 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'PENDING_DELETION'
  emailVerifiedAt?: string
  /** Lần đổi hoặc đặt lại mật khẩu gần nhất; trống nếu chưa từng đổi từ lúc tạo tài khoản. */
  passwordChangedAt?: string
  /** Tài khoản tạo qua nhà cung cấp định danh chưa có mật khẩu cho tới khi người học tự đặt. */
  hasPassword?: boolean
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

export interface ResetPasswordPayload {
  token: string
  password: string
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}

/** Trạng thái màn hình "đã gửi hướng dẫn đặt lại", giữ trong lịch sử trình duyệt qua lần tải lại trang. */
export interface ForgotPasswordSent {
  email: string
  resendAvailableAt: number
}

/** Nhà cung cấp định danh đang bật, ví dụ Google (NCL-01-CN-007). */
export interface OAuthProvider {
  id: string
  name: string
}

export type OAuthStatus = 'SIGNED_IN' | 'LINKED' | 'TERMS_REQUIRED' | 'LINK_REQUIRED'

/** Kết quả sau khi nhà cung cấp chuyển về, hoặc sau bước đồng ý điều khoản / nhập mật khẩu. */
export interface OAuthResult {
  status: OAuthStatus
  provider: string
  account?: Account
  /** Mã để tiếp tục bước còn dang dở, chỉ giữ trong bộ nhớ. */
  pendingToken?: string
  email?: string
  displayName?: string
  returnTo?: string
}

export interface LinkedIdentity {
  id: string
  provider: string
  providerName: string
  email: string
  linkedAt: string
  lastUsedAt?: string
}
