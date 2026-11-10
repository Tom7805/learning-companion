import type { useTranslation } from 'react-i18next'
import type { Citation } from '../types'

export type Translate = ReturnType<typeof useTranslation>['t']

/** Vị trí trích dẫn dễ đọc: "Trang 8", "Đoạn 3–5". */
export function locationLabel(citation: Pick<Citation, 'location'>, t: Translate) {
  const { kind, number, end } = citation.location
  if (kind === 'PARAGRAPH' && end > number) return t('assistant.location.PARAGRAPH_RANGE', { from: number, to: end })
  return t(`assistant.location.${kind}`, { number })
}
