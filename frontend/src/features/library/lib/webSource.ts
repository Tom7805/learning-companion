import type { ApiError } from '@/shared/api/errors'

/** Văn bản dán dài tối đa (khớp máy chủ); dài hơn thì nên tải lên dạng tệp. */
export const PASTE_TEXT_MAX = 200_000
export const TITLE_MAX = 200

export type WebImportProblem =
  | { kind: 'invalid' }
  | { kind: 'blocked' }
  | { kind: 'unavailable'; reason: string }
  | { kind: 'other'; code: string }

/** Lý do không lấy được trang mà máy chủ có thể trả về (NCL-05-CN-004 TC-04). */
const UNAVAILABLE_REASONS = ['LOGIN_REQUIRED', 'NOT_FOUND', 'TIMEOUT', 'TOO_LARGE', 'UNSUPPORTED', 'NO_CONTENT', 'UNREACHABLE']

/** Phân loại lỗi khi thêm trang web để giao diện giải thích và gợi ý dán văn bản thay thế. */
export function webImportProblem(error: ApiError): WebImportProblem {
  switch (error.code) {
    case 'WEB_ADDRESS_INVALID':
    case 'VALIDATION_FAILED':
      return { kind: 'invalid' }
    case 'WEB_ADDRESS_BLOCKED':
      return { kind: 'blocked' }
    case 'WEB_PAGE_UNAVAILABLE': {
      const reason = error.details?.reason
      return { kind: 'unavailable', reason: typeof reason === 'string' && UNAVAILABLE_REASONS.includes(reason) ? reason : 'UNREACHABLE' }
    }
    default:
      return { kind: 'other', code: error.code }
  }
}

/**
 * Kiểm sơ bộ ở giao diện để báo lỗi ngay khi gõ; máy chủ vẫn kiểm đầy đủ (mạng nội bộ, chuyển hướng). Thiếu giao thức
 * thì tự thêm https:// như thanh địa chỉ của trình duyệt.
 */
export function normalizeUrl(raw: string): string | null {
  const text = raw.trim()
  if (!text) return null
  const withScheme = /^[a-z][a-z0-9+.-]*:/i.test(text) ? text : `https://${text}`
  try {
    const url = new URL(withScheme)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
    if (!url.hostname) return null
    return url.toString()
  } catch {
    return null
  }
}
