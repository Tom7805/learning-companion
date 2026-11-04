import { describe, expect, it } from 'vitest'
import i18n from '@/shared/i18n'
import { canRetry, processingErrorMessage, processingSummary } from './processing'

const t = i18n.getFixedT('vi')

describe('processing', () => {
  it('giải thích lỗi theo mã, kèm trang lỗi khi có', () => {
    expect(processingErrorMessage({ status: 'FAILED', processing: { error: 'OCR_FAILED', errorPage: 3, retryable: true, chunks: 0 } }, t)).toBe(
      'Dịch vụ nhận dạng chữ gặp lỗi ở trang 3. Hãy thử lại sau ít phút.',
    )
    expect(processingErrorMessage({ status: 'FAILED', processing: { error: 'UNREADABLE', retryable: false, chunks: 0 } }, t)).toBe(
      'Không đọc được nội dung tệp, có thể tệp đã bị hỏng.',
    )
    expect(processingErrorMessage({ status: 'FAILED' }, t)).toBe('Hệ thống gặp sự cố khi xử lý tài liệu. Hãy thử lại.')
    expect(processingErrorMessage({ status: 'READY' }, t)).toBeNull()
  })

  it('chỉ lỗi tạm thời mới thử lại được', () => {
    expect(canRetry({ status: 'FAILED', processing: { error: 'INDEXING_FAILED', retryable: true, chunks: 0 } })).toBe(true)
    expect(canRetry({ status: 'FAILED', processing: { error: 'NO_TEXT', retryable: false, chunks: 0 } })).toBe(false)
    expect(canRetry({ status: 'READY', processing: { retryable: true, chunks: 1 } })).toBe(false)
  })

  it('tóm tắt theo loại tài liệu', () => {
    const done = '2026-11-04T03:00:00Z'
    expect(processingSummary({ status: 'READY', type: 'PPTX', processing: { units: 12, chunks: 14, retryable: false, processedAt: done } }, t)).toBe(
      '12 trang trình chiếu · 14 đoạn đã lập chỉ mục',
    )
    expect(processingSummary({ status: 'READY', type: 'IMAGE', processing: { units: 1, chunks: 2, retryable: false, processedAt: done } }, t)).toBe(
      '1 ảnh · 2 đoạn đã lập chỉ mục',
    )
    expect(processingSummary({ status: 'READY', type: 'DOCX', processing: { units: 40, chunks: 9, retryable: false, processedAt: done } }, t)).toBe(
      '40 đoạn văn · 9 đoạn đã lập chỉ mục',
    )
    // Tài liệu có từ trước, chưa qua xử lý: không có gì để tóm tắt.
    expect(processingSummary({ status: 'READY', type: 'TEXT', processing: { chunks: 0, retryable: false } }, t)).toBeNull()
  })
})
