export interface Account {
  id: string
  email: string
  displayName: string
  role: 'LEARNER' | 'ADMIN'
  status: 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'PENDING_DELETION'
  emailVerifiedAt?: string
}

export interface SessionStatus {
  authenticated: boolean
  account?: Account
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
