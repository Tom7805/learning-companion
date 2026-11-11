import type { useTranslation } from 'react-i18next'
import type { Citation } from '../types'

export type Translate = ReturnType<typeof useTranslation>['t']

/** Vị trí trích dẫn dễ đọc: "Trang 8", "Đoạn 3–5". */
export function locationLabel(citation: Pick<Citation, 'location'>, t: Translate) {
  const { kind, number, end } = citation.location
  if (kind === 'PARAGRAPH' && end > number) return t('assistant.location.PARAGRAPH_RANGE', { from: number, to: end })
  return t(`assistant.location.${kind}`, { number })
}

/**
 * Các tài liệu đã trích trong câu trả lời, mỗi tài liệu một lần theo thứ tự xuất hiện, kèm các số trích dẫn trỏ tới nó.
 * Tài liệu đã bị xóa hẳn (không còn mã) được nhóm theo tên.
 */
export function citedDocuments(citations: Citation[]) {
  const groups = new Map<string, { key: string; first: Citation; numbers: number[] }>()
  for (const citation of citations) {
    const key = citation.documentId ?? `gone:${citation.documentTitle}`
    const group = groups.get(key)
    if (group) group.numbers.push(citation.number)
    else groups.set(key, { key, first: citation, numbers: [citation.number] })
  }
  return [...groups.values()]
}
