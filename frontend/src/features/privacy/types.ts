export interface LegalDocument {
  version: string
  summary: string
  /** Điểm chính viết bằng lời dễ hiểu. */
  highlights?: string[]
  /** Điểm thay đổi so với phiên bản trước. */
  changes?: string[]
  effectiveAt: string
}

export interface LegalCurrent {
  terms: LegalDocument
  privacy: LegalDocument
}

/** Các mục đích không bắt buộc, đều tắt sẵn cho tới khi người học bật (QTN-03). */
export type OptionalPurpose = 'ANONYMOUS_USAGE_ANALYTICS' | 'PRODUCT_NEWS_EMAIL'
export type ConsentPurpose = OptionalPurpose | 'TERMS_OF_SERVICE' | 'PRIVACY_POLICY'

export interface PurposeConsent {
  purpose: OptionalPurpose
  granted: boolean
  documentVersion?: string | null
  updatedAt?: string | null
}

/** Trang quyền riêng tư của chính người học (NCL-01-CN-006). */
export interface PrivacyStatus {
  accountId: string
  terms: LegalDocument
  privacy: LegalDocument
  acceptedTermsVersion?: string | null
  acceptedPrivacyVersion?: string | null
  /** Điều khoản hoặc chính sách có phiên bản mới mà người học chưa đồng ý. */
  acceptanceRequired: boolean
  purposes: PurposeConsent[]
}

export interface ConsentHistoryEntry {
  id: string
  purpose: ConsentPurpose
  granted: boolean
  documentVersion: string
  recordedAt: string
}

export type DataExportStatus = 'QUEUED' | 'RUNNING' | 'READY' | 'FAILED' | 'EXPIRED'

/** Một phần dữ liệu trong gói xuất, do chức năng sở hữu dữ liệu đó đăng ký với bộ xuất. */
export interface DataExportSection {
  id: string
  title: string
  fileCount: number
}

/** Một lần xuất toàn bộ dữ liệu (NCL-01-CN-008); kích thước và các phần chỉ có khi gói sẵn sàng. */
export interface DataExport {
  id: string
  status: DataExportStatus
  requestedAt: string
  completedAt?: string | null
  expiresAt?: string | null
  sizeBytes?: number | null
  fileCount?: number | null
  fileName?: string | null
  sections: DataExportSection[]
  /** Chỉ có ở trang tải: lần tải tới cần nhập lại mật khẩu hoặc đăng nhập lại. */
  reauthenticationRequired?: boolean | null
}

export interface DataExportOverview {
  latest?: DataExport | null
  canRequest: boolean
  /** Mỗi ngày một yêu cầu: lúc được yêu cầu tiếp, trống khi yêu cầu được ngay. */
  nextAvailableAt?: string | null
}

export interface DataExportLink {
  url: string
  expiresAt: string
}
