import type { TFunction } from 'i18next'
import type { ApiError } from '@/shared/api/errors'

/**
 * Lời nhắn cho lỗi của lớp kết nối và cấu hình trí tuệ nhân tạo. Tìm bản dịch trong nhóm {@code prefix} trước, rồi
 * nhóm lỗi của lớp kết nối; chưa có bản dịch thì dùng lời nhắn máy chủ gửi.
 */
export function aiErrorMessage(t: TFunction, error: ApiError, prefix = 'aiGateway.errors') {
  const fallback = t(`aiGateway.errors.${error.code}`, {
    defaultValue: error.message || t('aiGateway.errors.generic'),
  })
  const base = prefix === 'aiGateway.errors' ? fallback : t(`${prefix}.${error.code}`, { defaultValue: fallback })
  const missing = Array.isArray(error.details?.missing) ? (error.details.missing as string[]) : []
  if (missing.length === 0) return base
  return `${base} ${t('aiGateway.errors.missingPrefix')} ${missing
    .map((item) => t(`aiGateway.requirements.${item}`, { defaultValue: item }))
    .join(', ')}.`
}
