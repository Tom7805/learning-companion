import type { TFunction } from 'i18next'
import type { ApiError } from '@/shared/api/errors'

/** Lời nhắn cho lỗi của lớp kết nối; lỗi chưa có bản dịch thì dùng lời nhắn máy chủ gửi. */
export function aiErrorMessage(t: TFunction, error: ApiError) {
  const key = `aiGateway.errors.${error.code}`
  const missing = Array.isArray(error.details?.missing) ? (error.details.missing as string[]) : []
  const base = t(key, { defaultValue: error.message || t('aiGateway.errors.generic') })
  if (missing.length === 0) return base
  return `${base} ${t('aiGateway.errors.missingPrefix')} ${missing
    .map((item) => t(`aiGateway.requirements.${item}`, { defaultValue: item }))
    .join(', ')}.`
}
