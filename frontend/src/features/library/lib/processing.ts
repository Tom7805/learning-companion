import type { TFunction } from 'i18next'
import type { DocumentCard } from '../types'

/**
 * Lời giải thích lỗi xử lý của một tài liệu (NCL-05-CN-003), kèm trang lỗi nếu có. Tài liệu không lỗi thì trả về
 * {@code null}.
 */
export function processingErrorMessage(document: Pick<DocumentCard, 'status' | 'processing'>, t: TFunction) {
  if (document.status !== 'FAILED') return null
  const error = document.processing?.error ?? 'INTERNAL'
  const page = document.processing?.errorPage
  if (error === 'OCR_FAILED' && page) return t('library.processing.errors.OCR_FAILED_PAGE', { page })
  return t(`library.processing.errors.${error}`, { defaultValue: t('library.processing.errors.INTERNAL') })
}

/** Tài liệu lỗi có thể thử lại (lỗi tạm thời của dịch vụ), hay cần tải bản khác. */
export function canRetry(document: Pick<DocumentCard, 'status' | 'processing'>) {
  return document.status === 'FAILED' && (document.processing?.retryable ?? false)
}

/**
 * Tóm tắt kết quả xử lý của tài liệu sẵn sàng: số trang (trang trình chiếu, ảnh, đoạn văn) và số đoạn đã lập chỉ
 * mục. Tài liệu chưa qua xử lý (ví dụ tạo trước khi có chức năng này) thì không có gì để tóm tắt.
 */
export function processingSummary(document: Pick<DocumentCard, 'status' | 'type' | 'processing'>, t: TFunction) {
  const info = document.processing
  if (document.status !== 'READY' || !info?.processedAt) return null
  const unitKey =
    document.type === 'PDF' ? 'pages' : document.type === 'PPTX' ? 'slides' : document.type === 'IMAGE' ? 'images' : 'paragraphs'
  const parts = [
    info.units ? t(`library.processing.units.${unitKey}`, { count: info.units }) : null,
    t('library.processing.chunks', { count: info.chunks }),
  ]
  return parts.filter(Boolean).join(' · ')
}
