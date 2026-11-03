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

/** Khả năng của lớp kết nối dịch vụ trí tuệ nhân tạo (NCL-04-CN-001). */
export type AiCapability = 'TEXT_GENERATION' | 'EMBEDDING' | 'VISION' | 'OCR'
export type AiAdapter = 'MOCK' | 'OPENAI_COMPATIBLE' | 'ANTHROPIC'
export type AiCircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN'

/** Nhà cung cấp như máy chủ trả về: chỉ có tên khóa trong kho bí mật, không bao giờ có giá trị khóa. */
export interface AiProvider {
  id: string
  code: string
  displayName: string
  adapter: AiAdapter
  baseUrl?: string | null
  textModel?: string | null
  embeddingModel?: string | null
  visionModel?: string | null
  capabilities: AiCapability[]
  adapterCapabilities: AiCapability[]
  secretName?: string | null
  secretRequired: boolean
  secretConfigured: boolean
  inputPricePerMillion: number
  outputPricePerMillion: number
  noTrainingConfirmed: boolean
  noTrainingConfirmedAt?: string | null
  enabled: boolean
  primaryFor: AiCapability[]
  fallbackFor: AiCapability[]
  updatedAt: string
}

export interface AiProviderInput {
  code?: string
  displayName: string
  adapter: AiAdapter
  baseUrl?: string
  textModel?: string
  embeddingModel?: string
  visionModel?: string
  capabilities: AiCapability[]
  secretName?: string
  inputPricePerMillion: number
  outputPricePerMillion: number
  noTrainingConfirmed: boolean
}

export interface AiRouteEndpoint {
  id: string
  code: string
  displayName: string
  enabled: boolean
  circuit: AiCircuitState
  recentFailures: number
  openedAt?: string | null
  retryAt?: string | null
}

export interface AiRoute {
  capability: AiCapability
  primary: AiRouteEndpoint
  fallback?: AiRouteEndpoint | null
  timeoutSeconds: number
  maxRetries: number
  breakerFailureThreshold: number
  breakerWindowSeconds: number
  breakerOpenSeconds: number
  updatedAt: string
}

export interface AiRouteInput {
  primaryProviderId: string
  fallbackProviderId?: string | null
  timeoutSeconds: number
  maxRetries: number
  breakerFailureThreshold: number
  breakerWindowSeconds: number
  breakerOpenSeconds: number
}

export interface AiGatewayAlert {
  id: string
  capability: AiCapability
  providerCode: string
  fallbackProviderCode?: string | null
  openedAt: string
}

export interface AiGatewayOverview {
  routes: AiRoute[]
  providers: AiProvider[]
  alerts: AiGatewayAlert[]
}

export interface AiUsageRow {
  capability: AiCapability
  providerCode?: string | null
  calls: number
  failures: number
  fallbackCalls: number
  inputUnits: number
  outputUnits: number
  costMicroUsd: number
  averageLatencyMs: number
}

export interface AiUsageCall {
  id: string
  occurredAt: string
  capability: AiCapability
  feature: string
  providerCode?: string | null
  outcome: 'SUCCESS' | 'FAILURE' | 'TIMEOUT' | 'UNAVAILABLE'
  usedFallback: boolean
  attempts: number
  inputUnits: number
  outputUnits: number
  latencyMs: number
  costMicroUsd: number
  errorCode?: string | null
}

export interface AiUsage {
  since: string
  totals: { calls: number; failures: number; inputUnits: number; outputUnits: number; costMicroUsd: number }
  rows: AiUsageRow[]
  recentCalls: AiUsageCall[]
}
