import type { DragEvent } from 'react'
import type { TFunction } from 'i18next'
import { detailNumber } from '@/shared/api/errors'
import { formatBytes } from '@/shared/lib/format'
import type { UploadItem } from '../hooks/useChunkedUpload'
import type { DuplicateInfo } from '../types'
import { DRAG_DOCUMENTS } from './drag'

type Translate = TFunction

/** Định dạng được nhận (QTN-19); máy chủ vẫn kiểm theo nội dung thật, đây chỉ để hộp chọn tệp lọc sẵn. */
export const UPLOAD_ACCEPT =
  '.pdf,.docx,.pptx,.epub,.txt,.md,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp,text/plain'

/** Kéo thả từ máy tính (không phải kéo tài liệu trong kho sang thư mục khác). */
export function isFileDrag(event: DragEvent) {
  const types = Array.from(event.dataTransfer?.types ?? [])
  return types.includes('Files') && !types.includes(DRAG_DOCUMENTS)
}

/** Vị trí thư mục dạng "Học kỳ một › Giải tích"; rỗng là thư mục gốc. */
export function duplicateLocation(info: DuplicateInfo, rootLabel: string) {
  return info.folderPath.length ? info.folderPath.join(' › ') : rootLabel
}

/** Lý do từ chối hay lỗi của một tệp, bằng lời dễ hiểu. */
export function uploadProblem(item: UploadItem, t: Translate, language: string) {
  const error = item.error
  if (!error) return ''
  if (error.code === 'UPLOAD_REJECTED') {
    const reason = typeof error.details?.reason === 'string' ? error.details.reason : 'UNSUPPORTED'
    return t(`library.upload.reasons.${reason}`, { defaultValue: t('library.upload.reasons.UNSUPPORTED') })
  }
  if (error.code === 'FILE_TOO_LARGE') {
    const max = detailNumber(error, 'maxBytes')
    return max ? t('library.upload.reasons.TOO_LARGE', { max: formatBytes(max, language) }) : t('errors.FILE_TOO_LARGE')
  }
  if (error.code === 'NOT_FOUND') return t('library.upload.folderGone')
  return t(`errors.${error.code}`, { defaultValue: t('library.upload.failed') })
}

/** Trạng thái một tệp bằng chữ, để không chỉ dựa vào màu hay biểu tượng. */
export function statusText(item: UploadItem, t: Translate, language: string) {
  const sent = formatBytes(item.sentBytes, language)
  const total = formatBytes(item.file.size, language)
  switch (item.state) {
    case 'queued':
      return t('library.upload.state.queued')
    case 'preparing':
      return t('library.upload.state.preparing')
    case 'uploading':
      return t('library.upload.state.uploading', { sent, total })
    case 'offline':
      return t('library.upload.state.offline', { sent, total })
    case 'verifying':
      return t('library.upload.state.verifying')
    case 'done':
      return t('library.upload.state.done', { folder: item.folderName })
    case 'duplicate':
      return item.duplicate
        ? t('library.upload.state.duplicate', { location: duplicateLocation(item.duplicate, t('library.shortcuts.root')) })
        : ''
    case 'cancelled':
      return t('library.upload.state.cancelled')
    default:
      return uploadProblem(item, t, language)
  }
}

