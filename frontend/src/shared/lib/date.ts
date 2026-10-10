const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
]

/**
 * "5 phút trước", "2 ngày trước"... theo ngôn ngữ giao diện; dưới một phút là "vừa xong".
 * Thời điểm trong tương lai (đồng hồ máy chủ và máy người dùng lệch nhau) cũng coi là "vừa xong".
 */
export function formatRelative(isoDate: string, language: string, now: number = Date.now()) {
  const seconds = Math.min(0, Math.round((new Date(isoDate).getTime() - now) / 1000))
  const formatter = new Intl.RelativeTimeFormat(language, { numeric: 'auto' })
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) {
      return formatter.format(Math.trunc(seconds / size), unit)
    }
  }
  return formatter.format(0, 'second')
}

export function formatDateTime(isoDate: string, language: string) {
  return new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(isoDate))
}
