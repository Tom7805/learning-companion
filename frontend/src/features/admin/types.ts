import type { Account } from '@/features/auth'

/** Quản trị viên đang đăng nhập (GET /admin/me). */
export interface AdminIdentity {
  id: string
  email: string
  displayName: string
  /** Hạn cứng của phiên quản trị: tám giờ kể từ lúc đăng nhập (NCL-03-CN-002). */
  sessionExpiresAt?: string | null
  recoveryCodesRemaining?: number
}

/** Bước mã xác thực khi quản trị viên đăng nhập: nhập mã (VERIFY) hay thiết lập ứng dụng sinh mã (ENROLL). */
export type TwoFactorStage = 'VERIFY' | 'ENROLL'

export interface TwoFactorChallenge {
  stage: TwoFactorStage
  email: string
  expiresAt: string
}

export interface TotpSetup {
  secret: string
  otpauthUri: string
}

export interface TwoFactorResult {
  signedIn: boolean
  stage?: TwoFactorStage | null
  account?: Account | null
  sessionExpiresAt?: string | null
  recoveryCodes: string[]
}

export type AuditCategory = 'LOGIN' | 'SESSION' | 'ACCOUNT' | 'PRIVACY' | 'SETTINGS' | 'ACCESS' | 'ADMIN'
export type AuditOutcome = 'SUCCESS' | 'FAILURE' | 'DENIED'
export type AuditActorType = 'USER' | 'ANONYMOUS' | 'SYSTEM'

export interface AuditAccount {
  id: string
  email: string
  displayName: string
}

/** Một dòng nhật ký thao tác: chỉ siêu dữ liệu, không có nội dung học tập (QTN-06). */
export interface AuditLogEntry {
  id: string
  occurredAt: string
  actorType: AuditActorType
  actorId?: string | null
  actor?: AuditAccount | null
  action: string
  category: AuditCategory
  outcome: AuditOutcome
  objectType: string
  objectId?: string | null
  /** Tài khoản mà thao tác nói tới, ví dụ tài khoản bị nhập sai mật khẩu. */
  subject?: AuditAccount | null
  device?: { browser: string; operatingSystem: string; deviceType: 'DESKTOP' | 'MOBILE' | 'TABLET' } | null
  ipAddress?: string | null
  details: Record<string, unknown>
  requestId?: string | null
}

export interface AuditLogPage {
  items: AuditLogEntry[]
  nextCursor?: string | null
}

/** Bộ lọc như trên đường dẫn; ngày dạng YYYY-MM-DD theo múi giờ hiển thị, cả hai đầu đều tính trọn ngày. */
export interface AuditLogFilters {
  account?: string
  category?: AuditCategory
  action?: string
  objectType?: string
  from?: string
  to?: string
}
