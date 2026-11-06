import { describe, expect, it } from 'vitest'
import { findMatches } from './viewerSearch'
import { parseViewerLocation, passageAt } from '../../hooks/useDocumentViewer'
import type { DocumentChunk } from '../../types'

const chunks: DocumentChunk[] = [
  { ordinal: 1, location: { kind: 'PAGE', number: 12, end: 12 }, content: 'Đạo hàm riêng bậc một', source: 'TEXT' },
  { ordinal: 2, location: { kind: 'PAGE', number: 12, end: 12 }, content: 'Ví dụ về tích phân', source: 'TEXT' },
  { ordinal: 3, location: { kind: 'PAGE', number: 12, end: 12 }, content: 'Đạo hàm riêng bậc hai', source: 'OCR' },
  { ordinal: 4, location: { kind: 'PAGE', number: 13, end: 13 }, content: 'Đạo hàm toàn phần', source: 'TEXT' },
]

describe('vị trí và tìm chữ trong khung xem', () => {
  it('mở đúng đoạn 3 ở trang 12 từ địa chỉ trang', () => {
    const location = parseViewerLocation(new URLSearchParams('kind=PAGE&number=12&passage=3'))
    expect(location).toEqual({ kind: 'PAGE', number: 12, passage: 3 })
    expect(passageAt(chunks, location)?.ordinal).toBe(3)
  })

  it('không chấp nhận vị trí không hợp lệ', () => {
    expect(parseViewerLocation(new URLSearchParams('kind=PAGE&number=-1'))).toBeNull()
    expect(parseViewerLocation(new URLSearchParams('kind=NOPE&number=12'))).toBeNull()
  })

  it('tìm mọi kết quả không phân biệt hoa thường và giữ thứ tự để chuyển tiếp', () => {
    expect(findMatches(chunks, 'ĐẠO HÀM RIÊNG').map((match) => match.chunk.ordinal)).toEqual([1, 3])
  })
})
